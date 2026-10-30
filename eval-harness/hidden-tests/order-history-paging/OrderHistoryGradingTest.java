package shop.grading;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shop.model.Customer;
import shop.model.Product;
import shop.service.OrderService;
import shop.service.OrderSummary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderHistoryGradingTest {

    private SessionFactory sf;
    private long customerId;
    private final Map<Long, Long> totals = new HashMap<>();
    private final OrderService service = new OrderService();

    @BeforeEach
    void setUp() {
        sf = GradingSupport.sessionFactory();
        var ids = sf.fromTransaction(s -> {
            var c = new Customer("Grace", "grace@example.com");
            var other = new Customer("Other", "other@example.com");
            s.persist(c);
            s.persist(other);
            List<Long> result = new ArrayList<>();
            result.add(c.getId());
            for (int i = 0; i < 3; i++) {
                var p = new Product("P" + i, new BigDecimal("10.00"));
                s.persist(p);
                result.add(p.getId());
            }
            result.add(other.getId());
            return result;
        });
        customerId = ids.get(0);
        long otherCustomer = ids.get(4);
        for (int i = 0; i < 25; i++) {
            int q = i % 3 + 1;
            Map<Long, Integer> quantities = Map.of(ids.get(1), 1, ids.get(2), q, ids.get(3), 2);
            Object orderId = sf.fromTransaction(s -> s.getIdentifier(service.placeOrder(s, customerId, quantities)));
            totals.put(((Number) orderId).longValue(), (long) (1 + q + 2));
        }
        sf.inTransaction(s -> service.placeOrder(s, otherCustomer, Map.of(ids.get(1), 5)));
    }

    @AfterEach
    void tearDown() {
        sf.close();
    }

    @Test
    void a1_correctPages() {
        var page0 = sf.fromTransaction(s -> service.orderHistory(s, customerId, 0));
        var page1 = sf.fromTransaction(s -> service.orderHistory(s, customerId, 1));
        assertThat(page0).hasSize(20);
        assertThat(page1).hasSize(5);
        List<OrderSummary> all = new ArrayList<>(page0);
        all.addAll(page1);
        assertThat(all).extracting(OrderSummary::orderId).containsExactlyInAnyOrderElementsOf(totals.keySet());
        for (OrderSummary summary : all) {
            assertThat(summary.totalQuantity()).isEqualTo(totals.get(summary.orderId()));
        }
        for (int i = 1; i < all.size(); i++) {
            assertThat(all.get(i).placedAt()).isBeforeOrEqualTo(all.get(i - 1).placedAt());
        }
    }

    @Test
    void a2_constantStatements() {
        long statements = GradingSupport.statements(sf, () ->
                sf.inTransaction(s -> service.orderHistory(s, customerId, 0)));
        assertThat(statements).isLessThanOrEqualTo(2);
    }

    @Test
    void a3_customerHasNoOrderCollection() {
        assertThat(GradingSupport.hasCollectionField(Customer.class)).isFalse();
    }

    @Test
    void a4_lazyAssociations() {
        assertThat(GradingSupport.eagerAssociations()).isEmpty();
    }
}
