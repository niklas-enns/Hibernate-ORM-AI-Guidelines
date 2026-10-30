---
tags: [skill-creator]
max_turns: 60
timeout_seconds: 1800
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit, Bash]
---

This is a small shop application using Hibernate ORM 7.4, Java 25 and Maven; tests run on H2 (see src/test/java/shop/TestDatabase.java). The SessionFactory is configured by the application, so your code receives a Session or SessionFactory and must not depend on extra configuration settings. Add tests for your code; `mvn test` must pass when you are done.

Add product categories. Every product belongs to exactly one category; a category contains at most a few dozen products.

- Create the entity `shop.model.Category` with a constructor `Category(String name)`, `getName()`, `getProducts()` and `addProduct(Product product)`.
- Create `shop.service.CatalogService` (public no-arg constructor) with `List<Category> categoryPage(SessionFactory sessionFactory, int pageNumber, int pageSize)`: categories ordered by name. The returned categories are rendered by a template engine **after** the method returned, and the template lists each category's products.
