package de.enns.niklas.hibernate;

import de.enns.niklas.hibernate.model.Address;
import de.enns.niklas.hibernate.model.Author;
import de.enns.niklas.hibernate.model.Book;
import de.enns.niklas.hibernate.model.Genre;
import de.enns.niklas.hibernate.model.Publisher;
import org.hibernate.SessionFactory;

final class TestData {

    private TestData() {
    }

    /** Persists {@code authors} authors with {@code booksPerAuthor} books each, all from one publisher. */
    static void createLibrary(SessionFactory sessionFactory, int authors, int booksPerAuthor) {
        sessionFactory.inTransaction(session -> {
            var publisher = new Publisher("Tor", new Address("Fifth Ave 1", "New York", "US"));
            session.persist(publisher);
            for (int a = 0; a < authors; a++) {
                var author = new Author("Author " + a);
                for (int b = 0; b < booksPerAuthor; b++) {
                    var book = new Book("isbn-" + a + "-" + b, "Book " + a + "/" + b, Genre.FANTASY);
                    book.setPublisher(publisher);
                    author.addBook(book);
                }
                session.persist(author);
            }
        });
    }
}
