package shop.grading;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shop.model.Category;
import shop.model.Product;
import shop.service.CatalogService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogGradingTest {

    private SessionFactory sf;
    private final CatalogService service = new CatalogService();
    private Object firstCategoryId;

    @BeforeEach
    void setUp() {
        sf = GradingSupport.sessionFactory();
        // insert in non-alphabetical order
        firstCategoryId = sf.fromTransaction(s -> {
            Object id = null;
            for (int i = 29; i >= 0; i--) {
                var category = new Category(String.format("Cat %02d", i));
                s.persist(category);
                for (int p = 0; p < 5; p++) {
                    var product = new Product(String.format("Cat %02d / P%d", i, p), new BigDecimal("1.00"));
                    category.addProduct(product);
                    s.persist(product);
                }
                if (i == 0) {
                    id = s.getIdentifier(category);
                }
            }
            return id;
        });
    }

    @AfterEach
    void tearDown() {
        sf.close();
    }

    @Test
    void b1_pageUsableAfterReturn() {
        var page = service.categoryPage(sf, 1, 10);
        List<String> names = new ArrayList<>();
        for (Category category : page) {
            names.add(category.getName());
            assertThat(category.getProducts().size()).isEqualTo(5);
            for (Object product : category.getProducts()) {
                assertThat(((Product) product).getName()).startsWith(category.getName());
            }
        }
        List<String> expected = new ArrayList<>();
        for (int i = 10; i < 20; i++) {
            expected.add(String.format("Cat %02d", i));
        }
        assertThat(names).containsExactlyElementsOf(expected);
    }

    @Test
    void b2_constantStatements() {
        long statements = GradingSupport.statements(sf, () -> {
            for (Category category : service.categoryPage(sf, 0, 10)) {
                category.getProducts().size();
            }
        });
        assertThat(statements).isLessThanOrEqualTo(2);
    }

    @Test
    void b3_owningSideWritten() {
        int size = sf.fromTransaction(s -> s.find(Category.class, firstCategoryId).getProducts().size());
        assertThat(size).isEqualTo(5);
    }

    @Test
    void b4_lazyAssociations() {
        assertThat(GradingSupport.eagerAssociations()).isEmpty();
    }
}
