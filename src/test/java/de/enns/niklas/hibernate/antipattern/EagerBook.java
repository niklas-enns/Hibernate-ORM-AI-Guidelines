package de.enns.niklas.hibernate.antipattern;

import de.enns.niklas.hibernate.model.Author;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** ANTI-PATTERN (A1): {@code @ManyToOne} without {@code fetch = LAZY} is EAGER by default. */
@Entity
public class EagerBook {

    @Id
    @GeneratedValue
    private Long id;

    private String title;

    @ManyToOne
    private Author author;

    protected EagerBook() {
    }

    public EagerBook(String title, Author author) {
        this.title = title;
        this.author = author;
    }

    public String getTitle() {
        return title;
    }
}
