package de.enns.niklas.hibernate;

import de.enns.niklas.hibernate.model.Address;
import de.enns.niklas.hibernate.model.Author;
import de.enns.niklas.hibernate.model.Book;
import de.enns.niklas.hibernate.model.Genre;
import de.enns.niklas.hibernate.model.Publisher;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionLifecycleTest extends HibernateTestBase {

    @Test
    @DisplayName("S1: changes to a managed entity are flushed on commit without any save/update call")
    void dirtyCheckingWritesChanges() {
        var author = new Author("Becky Chambers");
        sessionFactory.inTransaction(session -> session.persist(author));

        sessionFactory.inTransaction(session -> session.find(Author.class, author.getId()).setName("B. Chambers"));

        var reloaded = sessionFactory.fromTransaction(session -> session.find(Author.class, author.getId()));
        assertThat(reloaded.getName()).isEqualTo("B. Chambers");
    }

    @Test
    @DisplayName("S2: merge returns a managed copy; the passed instance stays detached")
    void mergeReturnsManagedCopy() {
        var detached = new Author("N. K. Jemisin");
        sessionFactory.inTransaction(session -> session.persist(detached));
        detached.setName("N.K. Jemisin");

        sessionFactory.inTransaction(session -> {
            var managed = session.merge(detached);
            assertThat(managed).isNotSameAs(detached);
            assertThat(session.contains(managed)).isTrue();
            assertThat(session.contains(detached)).isFalse();
        });

        var reloaded = sessionFactory.fromTransaction(session -> session.find(Author.class, detached.getId()));
        assertThat(reloaded.getName()).isEqualTo("N.K. Jemisin");
    }

    @Test
    @DisplayName("S5 anti-pattern: a regular session keeps every persisted entity in memory until it ends")
    void regularSessionKeepsEveryEntity() {
        sessionFactory.inTransaction(session -> {
            for (int i = 0; i < 500; i++) {
                session.persist(new Publisher("Publisher " + i, new Address("Street " + i, "City", "DE")));
            }
            session.flush(); // flushing writes the rows but does not release the entities

            assertThat(session.getStatistics().getEntityCount()).isEqualTo(500);
        });
    }

    @Test
    @DisplayName("S5: flush + clear every N entities bounds the persistence context to N")
    void flushAndClearBoundsMemory() {
        sessionFactory.inTransaction(session -> {
            int maxManaged = 0;
            for (int i = 0; i < 500; i++) {
                session.persist(new Publisher("Publisher " + i, new Address("Street " + i, "City", "DE")));
                maxManaged = Math.max(maxManaged, session.getStatistics().getEntityCount());
                if ((i + 1) % 50 == 0) {
                    session.flush();
                    session.clear();
                }
            }

            assertThat(maxManaged).isEqualTo(50);
            assertThat(session.getStatistics().getEntityCount()).isZero();
        });

        long count = sessionFactory.fromTransaction(session ->
                session.createSelectionQuery("select count(*) from Publisher", Long.class).getSingleResult());
        assertThat(count).isEqualTo(500);
    }

    @Test
    @DisplayName("S5: StatelessSession inserts in bulk without filling a persistence context")
    void statelessSessionForBulkInsert() {
        sessionFactory.inStatelessTransaction(session -> {
            for (int i = 0; i < 500; i++) {
                session.insert(new Publisher("Publisher " + i, new Address("Street " + i, "City", "DE")));
            }
        });

        long count = sessionFactory.fromTransaction(session ->
                session.createSelectionQuery("select count(*) from Publisher", Long.class).getSingleResult());
        assertThat(count).isEqualTo(500);
    }

    @Test
    @DisplayName("S5: setJdbcBatchSize on a StatelessSession batches inserts without global configuration")
    void statelessSessionBatchSize() {
        long unbatched = statementsDuring(() -> sessionFactory.inStatelessTransaction(session -> {
            for (int i = 0; i < 1_000; i++) {
                session.insert(new Publisher("Unbatched " + i, new Address("Street", "City", "DE")));
            }
        }));
        long batched = statementsDuring(() -> sessionFactory.inStatelessTransaction(session -> {
            session.setJdbcBatchSize(50);
            for (int i = 0; i < 1_000; i++) {
                session.insert(new Publisher("Batched " + i, new Address("Street", "City", "DE")));
            }
        }));

        // 1,000 inserts + 20 sequence calls (allocation size 50) without batching
        assertThat(unbatched).isGreaterThanOrEqualTo(1_000);
        assertThat(batched).isLessThan(unbatched / 10);
    }

    @Test
    @DisplayName("C1: @Version rejects merging a stale detached entity")
    void optimisticLockingRejectsStaleMerge() {
        var author = new Author("Martha Wells");
        var stale = new Book("978-0765397539", "All Systems Red", Genre.SCIENCE_FICTION);
        author.addBook(stale);
        sessionFactory.inTransaction(session -> session.persist(author));

        sessionFactory.inTransaction(session ->
                session.find(Book.class, stale.getId()).setTitle("All Systems Red (Murderbot 1)"));

        stale.setTitle("Concurrent edit");
        assertThatThrownBy(() -> sessionFactory.inTransaction(session -> session.merge(stale)))
                .isInstanceOf(OptimisticLockException.class);
    }
}
