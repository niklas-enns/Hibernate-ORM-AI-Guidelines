package de.enns.niklas.hibernate.antipattern;

import de.enns.niklas.hibernate.model.Author;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** ANTI-PATTERN (A3): cascading from child to parent. Removing a review removes the author. */
@Entity
public class CascadingReview {

    @Id
    @GeneratedValue
    private Long id;

    private String text;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Author author;

    protected CascadingReview() {
    }

    public CascadingReview(String text, Author author) {
        this.text = text;
        this.author = author;
    }

    public Long getId() {
        return id;
    }
}
