package de.enns.niklas.hibernate.antipattern;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class LazyToStringBook {

    @Id
    @GeneratedValue
    private Long id;

    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    private LazyToStringAuthor author;

    protected LazyToStringBook() {
    }

    public LazyToStringBook(String title, LazyToStringAuthor author) {
        this.title = title;
        this.author = author;
    }

    @Override
    public String toString() {
        return "LazyToStringBook{id=" + id + ", title='" + title + "'}";
    }
}
