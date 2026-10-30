package de.enns.niklas.hibernate.antipattern;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.util.Objects;

/** ANTI-PATTERN (M3): hashCode derived from a generated id changes when the entity is persisted. */
@Entity
public class IdHashedEntity {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    protected IdHashedEntity() {
    }

    public IdHashedEntity(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdHashedEntity other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
