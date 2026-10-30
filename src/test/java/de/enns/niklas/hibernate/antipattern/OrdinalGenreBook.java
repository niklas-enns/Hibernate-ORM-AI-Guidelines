package de.enns.niklas.hibernate.antipattern;

import de.enns.niklas.hibernate.model.Genre;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

/** ANTI-PATTERN (M4): {@code @Enumerated} without STRING stores the ordinal position. */
@Entity
public class OrdinalGenreBook {

    @Id
    @GeneratedValue
    private Long id;

    @Enumerated
    private Genre genre;

    protected OrdinalGenreBook() {
    }

    public OrdinalGenreBook(Genre genre) {
        this.genre = genre;
    }
}
