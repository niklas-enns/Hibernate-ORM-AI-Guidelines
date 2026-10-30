---
tags: [skill-creator]
max_turns: 60
timeout_seconds: 1800
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit, Bash]
---

This is a small shop application using Hibernate ORM 7.4, Java 25 and Maven; tests run on H2 (see src/test/java/shop/TestDatabase.java). The SessionFactory is configured by the application, so your code receives a Session or SessionFactory and must not depend on extra configuration settings. Add tests for your code; `mvn test` must pass when you are done.

Create `shop.service.ProductImporter` (public no-arg constructor) with `long importProducts(SessionFactory sessionFactory, Path csvFile)`. It imports products from a semicolon-separated UTF-8 file with the header line `name;price;stock` and returns the number of imported products. Supplier files can contain several hundred thousand rows.
