package de.enns.niklas.hibernate.antipattern;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.List;

/**
 * ANTI-PATTERN (M7): toString() includes a lazy collection, as Lombok's @ToString or @Data would generate.
 * Logging the entity triggers a query, or throws outside the session.
 */
@Entity
public class LazyToStringAuthor {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @OneToMany(mappedBy = "author")
    private List<LazyToStringBook> books = new ArrayList<>();

    protected LazyToStringAuthor() {
    }

    public LazyToStringAuthor(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    @Override
    public String toString() {
        return "LazyToStringAuthor{id=" + id + ", name='" + name + "', books=" + books + "}";
    }
}
