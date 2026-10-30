package de.enns.niklas.hibernate.antipattern;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

/** ANTI-PATTERN (M6): a final entity class cannot be proxied. */
@Entity
public final class FinalPublisher {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    protected FinalPublisher() {
    }

    public FinalPublisher(String name) {
        this.name = name;
    }
}
