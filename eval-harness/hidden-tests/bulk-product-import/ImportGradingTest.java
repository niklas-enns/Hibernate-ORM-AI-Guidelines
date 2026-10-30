package shop.grading;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import shop.model.Product;
import shop.service.ProductImporter;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImportGradingTest {

    private static final int ROWS = 50_000;

    @TempDir
    Path tempDir;
    private SessionFactory sf;
    private Path csv;

    @BeforeEach
    void setUp() throws IOException {
        sf = GradingSupport.sessionFactory();
        csv = tempDir.resolve("products.csv");
        StringBuilder content = new StringBuilder("name;price;stock\n");
        for (int i = 0; i < ROWS; i++) {
            content.append(String.format("Product-%05d;%d.99;%d%n", i, i % 1000, i % 50));
        }
        Files.writeString(csv, content.toString(), StandardCharsets.UTF_8);
    }

    @AfterEach
    void tearDown() {
        sf.close();
    }

    @Test
    void c1_importsAllRows() {
        long imported = new ProductImporter().importProducts(sf, csv);
        assertThat(imported).isEqualTo(ROWS);
        long count = sf.fromTransaction(s ->
                s.createSelectionQuery("select count(*) from Product", Long.class).getSingleResult());
        assertThat(count).isEqualTo(ROWS);
        for (int i : new int[]{0, 12_345, ROWS - 1}) {
            var product = sf.fromTransaction(s -> s.createSelectionQuery(
                    "from Product where name = :n", Product.class).setParameter("n", String.format("Product-%05d", i))
                    .getSingleResult());
            assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal((i % 1000) + ".99"));
            assertThat(product.getStock()).isEqualTo(i % 50);
        }
    }

    @Test
    void c2_boundedPersistenceContext() {
        String sources = GradingSupport.mainSources();
        boolean stateless = sources.matches("(?s).*\\b(StatelessSession|openStatelessSession|inStatelessTransaction|fromStatelessTransaction)\\b.*");
        boolean clears = sources.matches("(?s).*\\.clear\\(\\s*\\).*");
        assertThat(stateless || clears).as("uses StatelessSession or clears the session").isTrue();
    }

    @Test
    void c3_fastEnough() {
        long start = System.nanoTime();
        new ProductImporter().importProducts(sf, csv);
        long seconds = (System.nanoTime() - start) / 1_000_000_000L;
        assertThat(seconds).isLessThan(30);
    }
}
