package de.enns.niklas.hibernate.antipattern;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class NaiveBook {

    @Id
    @GeneratedValue
    private Long id;

    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    private NaiveAuthor author;

    protected NaiveBook() {
    }

    public NaiveBook(String title) {
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public NaiveAuthor getAuthor() {
        return author;
    }
}
