package de.enns.niklas.hibernate;

import de.enns.niklas.hibernate.antipattern.IdHashedEntity;
import de.enns.niklas.hibernate.antipattern.OrdinalGenreBook;
import de.enns.niklas.hibernate.model.Address;
import de.enns.niklas.hibernate.model.Author;
import de.enns.niklas.hibernate.model.Book;
import de.enns.niklas.hibernate.model.Genre;
import de.enns.niklas.hibernate.model.Publisher;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EntityMappingTest extends HibernateTestBase {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{Author.class, Book.class, Publisher.class, IdHashedEntity.class, OrdinalGenreBook.class};
    }

    @Test
    @DisplayName("M3 anti-pattern: hashCode from a generated id loses the entity in a HashSet after persist")
    void idBasedHashCodeBreaksHashSet() {
        var entity = new IdHashedEntity("lost");
        Set<IdHashedEntity> set = new HashSet<>();
        set.add(entity);

        sessionFactory.inTransaction(session -> session.persist(entity));

        assertThat(set.contains(entity)).isFalse();
    }

    @Test
    @DisplayName("M3: constant hashCode keeps the entity findable in a HashSet after persist")
    void constantHashCodeSurvivesPersist() {
        var author = new Author("Ursula K. Le Guin");
        Set<Author> set = new HashSet<>();
        set.add(author);

        sessionFactory.inTransaction(session -> session.persist(author));

        assertThat(author.getId()).isNotNull();
        assertThat(set.contains(author)).isTrue();
    }

    @Test
    @DisplayName("M3: equals uses getters, so it works against an uninitialized proxy")
    void equalsWorksWithProxies() {
        var author = new Author("Iain M. Banks");
        sessionFactory.inTransaction(session -> session.persist(author));

        sessionFactory.inTransaction(session -> {
            var proxy = session.getReference(Author.class, author.getId());
            assertThat(Hibernate.isInitialized(proxy)).isFalse();
            assertThat(author.equals(proxy)).isTrue();
        });
    }

    @Test
    @DisplayName("M3: natural-id equality holds across sessions and for transient instances")
    void naturalIdEquality() {
        var author = new Author("Frank Herbert");
        author.addBook(new Book("978-0441013593", "Dune", Genre.SCIENCE_FICTION));
        sessionFactory.inTransaction(session -> session.persist(author));

        var first = sessionFactory.fromTransaction(session ->
                session.bySimpleNaturalId(Book.class).load("978-0441013593"));
        var second = sessionFactory.fromTransaction(session ->
                session.bySimpleNaturalId(Book.class).load("978-0441013593"));

        assertThat(first).isNotSameAs(second).isEqualTo(second);
        assertThat(first).isEqualTo(new Book("978-0441013593", "any title", Genre.FANTASY));
    }

    @Test
    @DisplayName("M4 anti-pattern: @Enumerated without STRING stores the ordinal")
    void ordinalEnumStoresPosition() {
        sessionFactory.inTransaction(session -> session.persist(new OrdinalGenreBook(Genre.NON_FICTION)));

        Object stored = sessionFactory.fromTransaction(session ->
                session.createNativeQuery("select genre from OrdinalGenreBook", Object.class).getSingleResult());

        // reordering the enum constants would silently change the meaning of existing rows
        assertThat(stored).isInstanceOf(Number.class);
        assertThat(((Number) stored).intValue()).isEqualTo(Genre.NON_FICTION.ordinal());
    }

    @Test
    @DisplayName("M4: EnumType.STRING stores the constant name")
    void stringEnumStoresName() {
        var author = new Author("Mary Beard");
        author.addBook(new Book("978-1631492228", "SPQR", Genre.NON_FICTION));
        sessionFactory.inTransaction(session -> session.persist(author));

        Object stored = sessionFactory.fromTransaction(session ->
                session.createNativeQuery("select genre from Book", Object.class).getSingleResult());

        assertThat(stored).isEqualTo("NON_FICTION");
    }

    @Test
    @DisplayName("M5: a record works as an @Embeddable value type")
    void recordAsEmbeddable() {
        var publisher = new Publisher("Gollancz", new Address("Carmelite House", "London", "UK"));
        sessionFactory.inTransaction(session -> session.persist(publisher));

        var loaded = sessionFactory.fromTransaction(session -> session.find(Publisher.class, publisher.getId()));

        assertThat(loaded.getAddress()).isEqualTo(new Address("Carmelite House", "London", "UK"));
    }
}
