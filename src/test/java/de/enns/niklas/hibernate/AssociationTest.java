package de.enns.niklas.hibernate;

import de.enns.niklas.hibernate.antipattern.CascadingReview;
import de.enns.niklas.hibernate.antipattern.NaiveAuthor;
import de.enns.niklas.hibernate.antipattern.NaiveAward;
import de.enns.niklas.hibernate.antipattern.NaiveBook;
import de.enns.niklas.hibernate.model.Author;
import de.enns.niklas.hibernate.model.Book;
import de.enns.niklas.hibernate.model.Genre;
import de.enns.niklas.hibernate.model.Publisher;
import org.hibernate.loader.MultipleBagFetchException;
import org.hibernate.query.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class AssociationTest extends HibernateTestBase {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{Author.class, Book.class, Publisher.class,
                NaiveAuthor.class, NaiveBook.class, NaiveAward.class, CascadingReview.class};
    }

    @Test
    @DisplayName("A2 anti-pattern: changing only the inverse (mappedBy) side is not persisted")
    void inverseSideOnlyIsIgnored() {
        var bookId = sessionFactory.fromTransaction(session -> {
            var author = new NaiveAuthor("Naive");
            var book = new NaiveBook("Orphan");
            session.persist(author);
            session.persist(book);
            author.getBooks().add(book); // book.author is never set
            return book.getId();
        });

        var reloaded = sessionFactory.fromTransaction(session -> session.find(NaiveBook.class, bookId));

        assertThat(reloaded.getAuthor()).isNull();
    }

    @Test
    @DisplayName("A2: helper methods set the owning side, so the foreign key is written")
    void helperMethodSetsOwningSide() {
        sessionFactory.inTransaction(session -> {
            var author = new Author("Terry Pratchett");
            author.addBook(new Book("978-0062225672", "Guards! Guards!", Genre.FANTASY));
            session.persist(author);
        });

        var authorName = sessionFactory.fromTransaction(session ->
                session.createSelectionQuery("select b.author.name from Book b", String.class).getSingleResult());

        assertThat(authorName).isEqualTo("Terry Pratchett");
    }

    @Test
    @DisplayName("A3 anti-pattern: cascading REMOVE from child to parent deletes the parent and all its other children")
    void cascadeFromChildToParentDeletesSharedParent() {
        var author = new Author("Cascaded away");
        author.addBook(new Book("isbn-1", "First", Genre.FANTASY));
        author.addBook(new Book("isbn-2", "Second", Genre.FANTASY));
        var review = new CascadingReview("one bad review", author);
        sessionFactory.inTransaction(session -> {
            session.persist(author);
            session.persist(review);
        });

        // the intention: delete one review
        sessionFactory.inTransaction(session -> session.remove(session.find(CascadingReview.class, review.getId())));

        // the effect: the author is gone, and via Author.books (cascade ALL) both books too
        sessionFactory.inTransaction(session -> {
            assertThat(session.createSelectionQuery("select count(*) from Author", Long.class).getSingleResult())
                    .isZero();
            assertThat(session.createSelectionQuery("select count(*) from Book", Long.class).getSingleResult())
                    .isZero();
        });
    }

    @Test
    @DisplayName("A6: initializing a mapped collection loads every element; a query reads only what is needed")
    void mappedCollectionLoadsEverything() {
        TestData.createLibrary(sessionFactory, 1, 1_000);
        var authorId = sessionFactory.fromTransaction(session ->
                session.createSelectionQuery("select a.id from Author a", Long.class).getSingleResult());

        sessionFactory.inTransaction(session -> {
            var author = session.find(Author.class, authorId);
            assertThat(author.getBooks().size()).isEqualTo(1_000);
            // 1 author + 1,000 books are now managed: memory and dirty checking on every flush
            assertThat(session.getStatistics().getEntityCount()).isEqualTo(1 + 1_000);
        });

        sessionFactory.inTransaction(session -> {
            long count = session.createSelectionQuery(
                            "select count(*) from Book b where b.author.id = :id", Long.class)
                    .setParameter("id", authorId).getSingleResult();
            var firstPage = session.createSelectionQuery(
                            "from Book b where b.author.id = :id order by b.isbn", Book.class)
                    .setParameter("id", authorId).setPage(Page.first(20)).getResultList();
            assertThat(count).isEqualTo(1_000);
            assertThat(firstPage).hasSize(20);
            assertThat(session.getStatistics().getEntityCount()).isEqualTo(20);
        });
    }

    @Test
    @DisplayName("A4 anti-pattern: join fetching two List collections fails with MultipleBagFetchException")
    void twoBagsCannotBeFetchedTogether() {
        var thrown = catchThrowable(() -> sessionFactory.inTransaction(session ->
                session.createSelectionQuery(
                        "from NaiveAuthor a join fetch a.books join fetch a.awards", NaiveAuthor.class)
                        .getResultList()));

        assertThat(Stream.iterate(thrown, Objects::nonNull, Throwable::getCause))
                .anyMatch(MultipleBagFetchException.class::isInstance);
    }

    @Test
    @DisplayName("A4: fetch several collections with one query per collection in the same session")
    void fetchCollectionsInSeparateQueries() {
        sessionFactory.inTransaction(session -> {
            var author = new NaiveAuthor("Two collections");
            session.persist(author);
        });

        var statements = statementsDuring(() -> sessionFactory.inTransaction(session -> {
            var authors = session.createSelectionQuery(
                    "from NaiveAuthor a left join fetch a.books", NaiveAuthor.class).getResultList();
            session.createSelectionQuery(
                    "from NaiveAuthor a left join fetch a.awards where a in :authors", NaiveAuthor.class)
                    .setParameter("authors", authors)
                    .getResultList();
            authors.forEach(a -> {
                a.getBooks().size();
                a.getAwards().size();
            });
        }));

        assertThat(statements).isEqualTo(2);
    }

    @Test
    @DisplayName("A5: orphanRemoval deletes a child that is removed from the parent's collection")
    void orphanRemovalDeletesChild() {
        var author = new Author("Robin Hobb");
        var book = new Book("978-0553573398", "Assassin's Apprentice", Genre.FANTASY);
        author.addBook(book);
        sessionFactory.inTransaction(session -> session.persist(author));

        sessionFactory.inTransaction(session -> {
            var managed = session.find(Author.class, author.getId());
            managed.removeBook(managed.getBooks().iterator().next());
        });

        long books = sessionFactory.fromTransaction(session ->
                session.createSelectionQuery("select count(*) from Book", Long.class).getSingleResult());
        assertThat(books).isZero();
    }
}
