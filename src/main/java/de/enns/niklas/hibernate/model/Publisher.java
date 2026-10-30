package de.enns.niklas.hibernate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

/**
 * Referenced by {@link Book} through a unidirectional many-to-one; has no collection of books.
 * <p>
 * Rules shown: record as {@code @Embeddable} value type (M5), optimistic locking on a modifiable entity (C1).
 */
@Entity
public class Publisher {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Embedded
    private Address address;

    @Version
    private int version;

    protected Publisher() {
        // for Hibernate
    }

    public Publisher(String name, Address address) {
        this.name = name;
        this.address = address;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Publisher other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Publisher.class.hashCode();
    }

    @Override
    public String toString() {
        return "Publisher{id=" + id + ", name='" + name + "'}";
    }
}
