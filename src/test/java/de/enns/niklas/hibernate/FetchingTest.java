package de.enns.niklas.hibernate;

import de.enns.niklas.hibernate.antipattern.EagerBook;
import de.enns.niklas.hibernate.model.Author;
import de.enns.niklas.hibernate.model.Book;
import de.enns.niklas.hibernate.model.BookSummary;
import de.enns.niklas.hibernate.model.Publisher;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.hibernate.graph.GraphSemantic;
import org.hibernate.query.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FetchingTest extends HibernateTestBase {

    private static final int AUTHORS = 10;
    private static final int BOOKS_PER_AUTHOR = 3;

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{Author.class, Book.class, Publisher.class, EagerBook.class};
    }

    @BeforeEach
    void createData() {
        TestData.createLibrary(sessionFactory, AUTHORS, BOOKS_PER_AUTHOR);
    }

    @Test
    @DisplayName("A1 anti-pattern: an EAGER @ManyToOne causes N+1 selects even if never accessed")
    void eagerToOneCausesNPlusOne() {
        sessionFactory.inTransaction(session -> session
                .createSelectionQuery("from Author", Author.class)
                .getResultList()
                .forEach(author -> session.persist(new EagerBook("eager", author))));

        var statements = statementsDuring(() -> sessionFactory.inTransaction(session ->
                session.createSelectionQuery("from EagerBook", EagerBook.class).getResultList()));

        assertThat(statements).isEqualTo(1 + AUTHORS);
    }

    @Test
    @DisplayName("A1: LAZY to-one associations are not loaded by a plain query")
    void lazyToOneLoadsNothingExtra() {
        var statements = statementsDuring(() -> sessionFactory.inTransaction(session ->
                session.createSelectionQuery("from Book", Book.class).getResultList()));

        assertThat(statements).isEqualTo(1);
    }

    @Test
    @DisplayName("F1 anti-pattern: iterating a lazy collection per entity causes N+1 selects")
    void lazyCollectionInLoopCausesNPlusOne() {
        var statements = statementsDuring(() -> sessionFactory.inTransaction(session -> session
                .createSelectionQuery("from Author", Author.class)
                .getResultList()
                .forEach(author -> author.getBooks().size())));

        assertThat(statements).isEqualTo(1 + AUTHORS);
    }

    @Test
    @DisplayName("F1: join fetch loads the parents and their collections in one select")
    void joinFetchLoadsInOneSelect() {
        var statements = statementsDuring(() -> sessionFactory.inTransaction(session -> session
                .createSelectionQuery("from Author a join fetch a.books", Author.class)
                .getResultList()
                .forEach(author -> assertThat(author.getBooks()).hasSize(BOOKS_PER_AUTHOR))));

        assertThat(statements).isEqualTo(1);
    }

    @Test
    @DisplayName("F2: an entity graph fetches the collection without changing the query string")
    void entityGraphFetchesCollection() {
        var statements = statementsDuring(() -> sessionFactory.inTransaction(session -> {
            var graph = session.createEntityGraph(Author.class);
            graph.addAttributeNodes("books");
            var authors = session.createSelectionQuery("from Author", Author.class)
                    .setEntityGraph(graph, GraphSemantic.FETCH)
                    .getResultList();
            assertThat(authors).allMatch(author -> Hibernate.isInitialized(author.getBooks()));
        }));

        assertThat(statements).isEqualTo(1);
    }

    @Test
    @DisplayName("F4: pagination with a collection join fetch runs in SQL from 7.4.0; before, the safeguard rejects in-memory pagination")
    void paginationWithCollectionFetch() {
        Runnable pagedFetch = () -> sessionFactory.inTransaction(session -> {
            var page = session
                    .createSelectionQuery("from Author a join fetch a.books order by a.id", Author.class)
                    .setPage(Page.first(5))
                    .getResultList();
            assertThat(page).hasSize(5).allMatch(author -> author.getBooks().size() == BOOKS_PER_AUTHOR);
            assertThat(page).extracting(Author::getName)
                    .containsExactly("Author 0", "Author 1", "Author 2", "Author 3", "Author 4");
        });

        if (hibernateAtLeast(7, 4)) {
            // the root entities are paged in a derived table, collections are complete
            assertThat(statementsDuring(pagedFetch)).isEqualTo(1);
        } else {
            // up to 7.3 Hibernate would load ALL rows and page in memory;
            // fail_on_pagination_over_collection_fetch=true (set in HibernateTestBase) turns that into an error
            assertThatThrownBy(pagedFetch::run).hasMessageContaining("in-memory pagination");
        }
    }

    @Test
    @DisplayName("F4: portable alternative: paginate ids first, then join fetch the page")
    void paginateIdsThenFetch() {
        var statements = statementsDuring(() -> sessionFactory.inTransaction(session -> {
            var ids = session.createSelectionQuery("select a.id from Author a order by a.id", Long.class)
                    .setPage(Page.first(5))
                    .getResultList();
            var page = session.createSelectionQuery(
                            "from Author a join fetch a.books where a.id in :ids order by a.id", Author.class)
                    .setParameter("ids", ids)
                    .getResultList();
            assertThat(page).hasSize(5).allMatch(author -> author.getBooks().size() == BOOKS_PER_AUTHOR);
        }));

        assertThat(statements).isEqualTo(2);
    }

    @Test
    @DisplayName("F5: a record DTO projection reads exactly the needed columns in one select")
    void recordProjection() {
        var statements = statementsDuring(() -> sessionFactory.inTransaction(session -> {
            var summaries = session.createSelectionQuery(
                            "select b.isbn, b.title, b.author.name from Book b order by b.isbn", BookSummary.class)
                    .getResultList();
            assertThat(summaries).hasSize(AUTHORS * BOOKS_PER_AUTHOR);
            assertThat(summaries.getFirst()).isEqualTo(new BookSummary("isbn-0-0", "Book 0/0", "Author 0"));
        }));

        assertThat(statements).isEqualTo(1);
    }

    @Test
    @DisplayName("F6 anti-pattern: touching a lazy association after the session closed throws")
    void lazyAccessOutsideSessionFails() {
        var author = sessionFactory.fromTransaction(session ->
                session.createSelectionQuery("from Author order by id", Author.class)
                        .setMaxResults(1)
                        .getSingleResult());

        assertThatThrownBy(() -> author.getBooks().size()).isInstanceOf(LazyInitializationException.class);
    }
}
