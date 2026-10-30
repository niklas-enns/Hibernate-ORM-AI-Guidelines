---
tags: [skill-creator]
max_turns: 60
timeout_seconds: 1800
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit, Bash]
---

This is a small shop application using Hibernate ORM 7.4, Java 25 and Maven; tests run on H2 (see src/test/java/shop/TestDatabase.java). The SessionFactory is configured by the application, so your code receives a Session or SessionFactory and must not depend on extra configuration settings. Add tests for your code; `mvn test` must pass when you are done.

Add ordering. A customer places purchase orders; each order has one or more lines (a product and a quantity). Customers keep ordering over many years.

- Create the entities `shop.model.PurchaseOrder` and `shop.model.OrderLine`.
- Create `shop.service.OrderService` (public no-arg constructor) with:
  - `PurchaseOrder placeOrder(Session session, long customerId, Map<Long, Integer> quantitiesByProductId)`: creates and persists an order placed now. The caller manages the transaction.
  - `List<OrderSummary> orderHistory(Session session, long customerId, int pageNumber)`: the customer's orders, newest first, 20 per page (page 0 is the first page).
- `shop.service.OrderSummary` is a record `(long orderId, LocalDateTime placedAt, long totalQuantity)`, where totalQuantity is the sum of the quantities of the order's lines.
