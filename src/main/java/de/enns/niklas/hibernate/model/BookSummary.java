package de.enns.niklas.hibernate.model;

/** Read-only DTO projection; filled directly by a query, never managed by the session (F5). */
public record BookSummary(String isbn, String title, String authorName) {
}
