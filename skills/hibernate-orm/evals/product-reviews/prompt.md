---
tags: [skill-creator]
max_turns: 60
timeout_seconds: 1800
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit, Bash]
---

This is a small shop application using Hibernate ORM 7.4, Java 25 and Maven; tests run on H2 (see src/test/java/shop/TestDatabase.java). The SessionFactory is configured by the application, so your code receives a Session or SessionFactory and must not depend on extra configuration settings. Add tests for your code; `mvn test` must pass when you are done.

Customers can review products with a rating from 1 to 5 and a text. Popular products get thousands of reviews.

- Create the entity `shop.model.Review` with a constructor `Review(Customer customer, Product product, int rating, String text)` and `getId()`.
- Create `shop.service.ReviewService` (public no-arg constructor) with:
  - `void deleteReview(SessionFactory sessionFactory, long reviewId)`
  - `RatingSummary ratingSummary(SessionFactory sessionFactory, long productId)`: number of reviews and average rating (0 when there are none).
- `shop.service.RatingSummary` is a record `(long reviewCount, double averageRating)`.
