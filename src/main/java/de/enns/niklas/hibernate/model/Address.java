package de.enns.niklas.hibernate.model;

import jakarta.persistence.Embeddable;

/**
 * Immutable value type. Records work as embeddables, not as entities (M5).
 * To change it, replace the whole value: {@code publisher.setAddress(new Address(...))}.
 */
@Embeddable
public record Address(String street, String city, String country) {
}
