package de.enns.niklas.hibernate.antipattern;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.List;

/**
 * ANTI-PATTERNS: exposes mutable inverse-side collections without helper methods (A2),
 * and has two {@code List} collections that cannot be fetched together (A4).
 */
@Entity
public class NaiveAuthor {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @OneToMany(mappedBy = "author")
    private List<NaiveBook> books = new ArrayList<>();

    @OneToMany(mappedBy = "author")
    private List<NaiveAward> awards = new ArrayList<>();

    protected NaiveAuthor() {
    }

    public NaiveAuthor(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public List<NaiveBook> getBooks() {
        return books;
    }

    public List<NaiveAward> getAwards() {
        return awards;
    }
}
