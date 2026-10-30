package de.enns.niklas.hibernate.antipattern;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** Mapped correctly with a LAZY to-one, but the target class {@link FinalPublisher} is final. */
@Entity
public class FinalPublisherBook {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private FinalPublisher publisher;

    protected FinalPublisherBook() {
    }

    public FinalPublisherBook(FinalPublisher publisher) {
        this.publisher = publisher;
    }
}
