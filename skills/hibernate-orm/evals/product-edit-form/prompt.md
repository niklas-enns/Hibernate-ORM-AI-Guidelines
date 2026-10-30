---
tags: [skill-creator]
max_turns: 60
timeout_seconds: 1800
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit, Bash]
---

This is a small shop application using Hibernate ORM 7.4, Java 25 and Maven; tests run on H2 (see src/test/java/shop/TestDatabase.java). The SessionFactory is configured by the application, so your code receives a Session or SessionFactory and must not depend on extra configuration settings. Add tests for your code; `mvn test` must pass when you are done.

The admin UI has a form to edit a product's name and price. It submits a `shop.service.ProductForm` record `(long id, String name, BigDecimal price)`.

Create `shop.service.ProductAdminService` (public no-arg constructor) with `void updateProduct(SessionFactory sessionFactory, ProductForm form)` that applies the form to the product.
