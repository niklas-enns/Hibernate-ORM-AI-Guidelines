package de.enns.niklas.hibernate;

import de.enns.niklas.hibernate.antipattern.FinalPublisher;
import de.enns.niklas.hibernate.antipattern.FinalPublisherBook;
import de.enns.niklas.hibernate.antipattern.LazyToStringAuthor;
import de.enns.niklas.hibernate.antipattern.LazyToStringBook;
import de.enns.niklas.hibernate.model.Address;
import de.enns.niklas.hibernate.model.Author;
import de.enns.niklas.hibernate.model.Book;
import de.enns.niklas.hibernate.model.Genre;
import de.enns.niklas.hibernate.model.Publisher;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassShapeTest extends HibernateTestBase {

    private static final int BOOKS = 10;

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{Author.class, Book.class, Publisher.class,
                FinalPublisher.class, FinalPublisherBook.class, LazyToStringAuthor.class, LazyToStringBook.class};
    }

    @Test
    @DisplayName("M6 anti-pattern: a LAZY to-one pointing to a final entity class is loaded anyway")
    void finalEntityCannotBeLazy() {
        sessionFactory.inTransaction(session -> {
            for (int i = 0; i < BOOKS; i++) {
                var publisher = new FinalPublisher("Final " + i);
                session.persist(publisher);
                session.persist(new FinalPublisherBook(publisher));
            }
        });

        var statements = statementsDuring(() -> sessionFactory.inTransaction(session ->
                session.createSelectionQuery("from FinalPublisherBook", FinalPublisherBook.class).getResultList()));

        assertThat(statements).isEqualTo(1 + BOOKS);
    }

    @Test
    @DisplayName("M6: a LAZY to-one pointing to a non-final entity class is not loaded")
    void nonFinalEntityStaysLazy() {
        sessionFactory.inTransaction(session -> {
            for (int i = 0; i < BOOKS; i++) {
                var publisher = new Publisher("Publisher " + i, new Address("Street", "City", "DE"));
                session.persist(publisher);
                var author = new Author("Author " + i);
                var book = new Book("isbn-" + i, "Book " + i, Genre.FANTASY);
                book.setPublisher(publisher);
                author.addBook(book);
                session.persist(author);
            }
        });

        var statements = statementsDuring(() -> sessionFactory.inTransaction(session ->
                session.createSelectionQuery("from Book", Book.class).getResultList()));

        assertThat(statements).isEqualTo(1);
    }

    @Test
    @DisplayName("M7 anti-pattern: toString() over a lazy collection queries inside the session and throws outside")
    void toStringWithLazyCollection() {
        var authorId = sessionFactory.fromTransaction(session -> {
            var author = new LazyToStringAuthor("Logged");
            session.persist(author);
            session.persist(new LazyToStringBook("One", author));
            return author.getId();
        });

        var statements = statementsDuring(() -> sessionFactory.inTransaction(session -> {
            var author = session.find(LazyToStringAuthor.class, authorId);
            String logged = author.toString(); // e.g. a log statement
            assertThat(logged).contains("One");
        }));
        assertThat(statements).isEqualTo(2); // find + the collection, just for logging

        var detached = sessionFactory.fromTransaction(session -> session.find(LazyToStringAuthor.class, authorId));
        assertThatThrownBy(detached::toString).isInstanceOf(LazyInitializationException.class);
    }

    @Test
    @DisplayName("M7: toString() without associations never touches the database")
    void toStringWithoutAssociations() {
        var author = new Author("Safe to log");
        sessionFactory.inTransaction(session -> session.persist(author));

        var statements = statementsDuring(() -> sessionFactory.inTransaction(session ->
                assertThat(session.find(Author.class, author.getId()).toString()).contains("Safe to log")));

        assertThat(statements).isEqualTo(1);
    }
}
