package shop.grading;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shop.model.Customer;
import shop.model.Product;
import shop.model.Review;
import shop.service.RatingSummary;
import shop.service.ReviewService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewGradingTest {

    private SessionFactory sf;
    private final ReviewService service = new ReviewService();
    private long customerId;
    private long productId;
    private long emptyProductId;
    private final List<Long> reviewIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sf = GradingSupport.sessionFactory();
        sf.inTransaction(s -> {
            var customer = new Customer("Rita", "rita@example.com");
            var product = new Product("Reviewed", new BigDecimal("5.00"));
            var empty = new Product("Unreviewed", new BigDecimal("5.00"));
            s.persist(customer);
            s.persist(product);
            s.persist(empty);
            for (int i = 0; i < 200; i++) {
                var review = new Review(customer, product, i % 5 + 1, "Review " + i);
                s.persist(review);
            }
            s.flush();
            customerId = customer.getId();
            productId = product.getId();
            emptyProductId = empty.getId();
        });
        reviewIds.addAll(sf.fromTransaction(s -> s.createSelectionQuery(
                "select r.id from Review r order by r.id", Long.class).getResultList()));
    }

    @AfterEach
    void tearDown() {
        sf.close();
    }

    @Test
    void e1_deleteOnlyTheReview() {
        service.deleteReview(sf, reviewIds.getFirst());
        sf.inTransaction(s -> {
            assertThat(s.find(Customer.class, customerId)).isNotNull();
            assertThat(s.find(Product.class, productId)).isNotNull();
            assertThat(s.createSelectionQuery("select count(*) from Review", Long.class).getSingleResult())
                    .isEqualTo(199);
        });
    }

    @Test
    void e2_correctSummary() {
        RatingSummary summary = service.ratingSummary(sf, productId);
        assertThat(summary.reviewCount()).isEqualTo(200);
        assertThat(summary.averageRating()).isEqualTo(3.0);
        RatingSummary none = service.ratingSummary(sf, emptyProductId);
        assertThat(none.reviewCount()).isZero();
        assertThat(none.averageRating()).isZero();
    }

    @Test
    void e3_singleAggregateQuery() {
        var statistics = sf.getStatistics();
        statistics.clear();
        service.ratingSummary(sf, productId);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityLoadCount()).isZero();
    }

    @Test
    void e4_productHasNoReviewCollection() {
        assertThat(GradingSupport.hasCollectionField(Product.class)).isFalse();
    }

    @Test
    void e5_noCascadeFromReview() {
        assertThat(GradingSupport.cascadingToOne()).isEmpty();
    }
}
