package shop.grading;

import jakarta.persistence.Version;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shop.model.Product;
import shop.service.ProductAdminService;
import shop.service.ProductForm;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductEditGradingTest {

    private SessionFactory sf;
    private long productId;
    private final ProductAdminService service = new ProductAdminService();

    @BeforeEach
    void setUp() {
        sf = GradingSupport.sessionFactory();
        productId = sf.fromTransaction(s -> {
            var p = new Product("Old name", new BigDecimal("10.00"));
            p.setStock(7);
            p.setDescription("Original description");
            s.persist(p);
            return p.getId();
        });
    }

    @AfterEach
    void tearDown() {
        sf.close();
    }

    private Product reload() {
        return sf.fromTransaction(s -> s.find(Product.class, productId));
    }

    @Test
    void d1_updatesNameAndPrice() {
        service.updateProduct(sf, new ProductForm(productId, "New name", new BigDecimal("12.50")));
        var p = reload();
        assertThat(p.getName()).isEqualTo("New name");
        assertThat(p.getPrice()).isEqualByComparingTo("12.50");
    }

    @Test
    void d2_keepsOtherFields() {
        service.updateProduct(sf, new ProductForm(productId, "New name", new BigDecimal("12.50")));
        var p = reload();
        assertThat(p.getStock()).isEqualTo(7);
        assertThat(p.getDescription()).isEqualTo("Original description");
    }

    @Test
    void d3_unknownIdCreatesNothing() {
        try {
            service.updateProduct(sf, new ProductForm(productId + 999, "Ghost", new BigDecimal("1.00")));
        } catch (RuntimeException expectedOrNot) {
            // throwing is acceptable; creating a product is not
        }
        long count = sf.fromTransaction(s ->
                s.createSelectionQuery("select count(*) from Product", Long.class).getSingleResult());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void d4_productHasVersion() {
        assertThat(GradingSupport.fields(Product.class)).anyMatch(f -> f.isAnnotationPresent(Version.class));
    }
}
