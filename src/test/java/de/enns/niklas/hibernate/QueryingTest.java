package de.enns.niklas.hibernate;

import de.enns.niklas.hibernate.model.Author;
import de.enns.niklas.hibernate.model.Book;
import de.enns.niklas.hibernate.model.Genre;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryingTest extends HibernateTestBase {

    @Test
    @DisplayName("Q1: bound parameters treat user input as a value, never as query text")
    void parametersAreNotInterpreted() {
        sessionFactory.inTransaction(session -> {
            session.persist(new Author("O'Reilly"));
            session.persist(new Author("Someone else"));
        });

        sessionFactory.inTransaction(session -> {
            var query = "from Author where name = :name";
            assertThat(session.createSelectionQuery(query, Author.class)
                    .setParameter("name", "O'Reilly").getResultList()).hasSize(1);
            assertThat(session.createSelectionQuery(query, Author.class)
                    .setParameter("name", "x' or '1'='1").getResultList()).isEmpty();
        });
    }

    @Test
    @DisplayName("Q3: join fetch returns each root entity once, no DISTINCT needed")
    void joinFetchDeduplicatesRoots() {
        TestData.createLibrary(sessionFactory, 2, 3);

        var authors = sessionFactory.fromTransaction(session -> session
                .createSelectionQuery("from Author a join fetch a.books", Author.class)
                .getResultList());

        assertThat(authors).hasSize(2);
    }

    @Test
    @DisplayName("Q5 anti-pattern: restricting a join-fetched collection leaves the entity with a partial collection")
    void filteringFetchedCollectionCorruptsState() {
        TestData.createLibrary(sessionFactory, 1, 3);

        sessionFactory.inTransaction(session -> {
            var author = session.createSelectionQuery(
                            "from Author a join fetch a.books b where b.isbn = 'isbn-0-0'", Author.class)
                    .getSingleResult();
            // the managed entity now claims the author has 1 book; a flush with orphanRemoval could act on this
            assertThat(author.getBooks()).hasSize(1);
        });
    }

    @Test
    @DisplayName("Q5: filter with a separate join and fetch the full collection")
    void filterWithSeparateJoin() {
        TestData.createLibrary(sessionFactory, 1, 3);

        sessionFactory.inTransaction(session -> {
            var author = session.createSelectionQuery(
                            "from Author a join fetch a.books where exists "
                                    + "(select 1 from Book b where b.author = a and b.isbn = 'isbn-0-0')", Author.class)
                    .getSingleResult();
            assertThat(author.getBooks()).hasSize(3);
        });
    }

    @Test
    @DisplayName("Q4 anti-pattern: a bulk update leaves already loaded entities stale")
    void bulkUpdateBypassesPersistenceContext() {
        var author = new Author("Ann Leckie");
        author.addBook(new Book("978-0316246620", "Ancillary Justice", Genre.SCIENCE_FICTION));
        sessionFactory.inTransaction(session -> session.persist(author));

        sessionFactory.inTransaction(session -> {
            var book = session.bySimpleNaturalId(Book.class).load("978-0316246620");

            int updated = session.createMutationQuery("update Book set title = upper(title)").executeUpdate();

            assertThat(updated).isEqualTo(1);
            assertThat(book.getTitle()).isEqualTo("Ancillary Justice"); // stale
            session.refresh(book);
            assertThat(book.getTitle()).isEqualTo("ANCILLARY JUSTICE");
        });
    }
}
