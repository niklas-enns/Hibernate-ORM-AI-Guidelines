package de.enns.niklas.hibernate;

import de.enns.niklas.hibernate.model.Author;
import org.hibernate.cfg.FetchSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BatchFetchingTest extends HibernateTestBase {

    @Override
    protected Map<String, Object> settings() {
        return Map.of(FetchSettings.DEFAULT_BATCH_FETCH_SIZE, 16);
    }

    @Test
    @DisplayName("F3: default_batch_fetch_size turns N+1 into 1 + ceil(N / batch size) selects")
    void batchFetchingLimitsNPlusOne() {
        TestData.createLibrary(sessionFactory, 10, 3);

        var statements = statementsDuring(() -> sessionFactory.inTransaction(session -> session
                .createSelectionQuery("from Author", Author.class)
                .getResultList()
                .forEach(author -> author.getBooks().size())));

        assertThat(statements).isEqualTo(2);
    }
}
