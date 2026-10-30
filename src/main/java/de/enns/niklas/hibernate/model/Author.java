package de.enns.niklas.hibernate.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Parent side of a bidirectional one-to-many.
 * <p>
 * Rules shown: SEQUENCE ids (M2), id-based equals/hashCode with a constant hashCode (M3),
 * inverse side with {@code mappedBy} and helper methods (A2), cascade + orphanRemoval only
 * from parent to child (A3, A5), optimistic locking on a modifiable entity (C1).
 */
@Entity
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Book> books = new HashSet<>();

    @Version
    private int version;

    protected Author() {
        // for Hibernate
    }

    public Author(String name) {
        this.name = name;
    }

    public void addBook(Book book) {
        books.add(book);
        book.setAuthor(this);
    }

    public void removeBook(Book book) {
        books.remove(book);
        book.setAuthor(null);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Book> getBooks() {
        return Collections.unmodifiableSet(books);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Author other)) return false;
        // use the getter: 'other' may be an uninitialized proxy whose fields are null
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // constant, so the hash does not change when the id is assigned on persist
        return Author.class.hashCode();
    }

    @Override
    public String toString() {
        // never include lazy associations here
        return "Author{id=" + id + ", name='" + name + "'}";
    }
}
