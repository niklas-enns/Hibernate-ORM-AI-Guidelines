package shop;

import org.junit.jupiter.api.Test;
import shop.model.Customer;
import shop.model.Product;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeTest {

    @Test
    void persistsCustomerAndProduct() {
        try (var sessionFactory = TestDatabase.create(Customer.class, Product.class)) {
            sessionFactory.inTransaction(session -> {
                session.persist(new Customer("Ada", "ada@example.com"));
                session.persist(new Product("Keyboard", new BigDecimal("49.90")));
            });
            long count = sessionFactory.fromTransaction(session ->
                    session.createSelectionQuery("select count(*) from Product", Long.class).getSingleResult());
            assertThat(count).isEqualTo(1);
        }
    }
}
