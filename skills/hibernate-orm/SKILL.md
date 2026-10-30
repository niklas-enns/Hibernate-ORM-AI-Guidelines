---
name: hibernate-orm
description: Rules and verified patterns for writing Hibernate ORM 7 / Jakarta Persistence 3.2 code in plain Java. Use when creating or changing JPA entities (@Entity, @Embeddable), associations (@OneToMany, @ManyToOne, @ManyToMany), HQL/JPQL/native queries, entity graphs, Session/EntityManager/StatelessSession usage, transactions, optimistic locking, or when diagnosing N+1 selects, LazyInitializationException or MultipleBagFetchException.
---

# Hibernate ORM 7 guidelines

Target: **Hibernate ORM 7.x** with **Jakarta Persistence 3.2**, plain Java. Rules tagged ✅ are backed by tests
that pass on Hibernate 7.0 through 7.4.

You already know general Hibernate practice. This skill adds what gets missed without it: a **checklist for
every entity**, this project's **house rules**, and **Hibernate 7 specifics**. Hibernate 5 examples from
memory are often wrong for version 7.

## Workflow

1. Go through the **entity checklist** for every entity you create or change, including entities you only
   touch to add an association. Missing `LAZY` and missing `@Version` are the most common slips.
2. Write the code. Open a reference file only when you need details or examples:
   [entity-mapping](references/entity-mapping.md), [associations](references/associations.md),
   [fetching](references/fetching.md), [querying](references/querying.md),
   [sessions-and-transactions](references/sessions-and-transactions.md), [testing](references/testing.md),
   [anti-patterns](references/anti-patterns.md) (review checklist).
3. Run the checker on the changed code. Fix every ERROR, and fix WARNINGs unless you can say why one doesn't
   apply: `python3 <skill-dir>/scripts/check_entities.py src/main/java`
4. For code that loads data, assert the SQL statement count in a test (`references/testing.md`).

## Core rules

### Entity checklist: every entity you create or change

- **A1** ✅ Every `@ManyToOne` and `@OneToOne` has `fetch = FetchType.LAZY`. The default is EAGER, which loads
  the target for every row, even if it's never used. Never use `FetchType.EAGER`.
- **C1** ✅ `@Version private int version;` on every entity that is **modified after creation**. Skip it only
  for insert-only/immutable entities. Never set it yourself.
- **M3** ✅ `equals`/`hashCode`: compare a `@NaturalId` if one exists. Otherwise compare the non-null generated
  id and return a **constant** `hashCode` (e.g. `Order.class.hashCode()`). Use `instanceof` and getters on the
  other object, because it may be a proxy. Never `Objects.hash(id)`.
- **M7** ✅ `toString()` contains no associations. No Lombok `@Data`/`@EqualsAndHashCode`/`@ToString` on
  entities (`@Getter`/`@Setter` are fine).
- **M4** ✅ Enums are `@Enumerated(EnumType.STRING)`.
- **M6** ✅ Non-`final` class with a `protected` no-arg constructor. Hibernate can't proxy a final class, so
  LAZY associations to it are silently loaded eagerly.
- **A6** ✅ A `@OneToMany` collection only if the domain **bounds** it (order lines, invoice positions).
  Children that accumulate over time (orders, reviews, events, transactions) get only the `@ManyToOne` on the
  child and are queried with paging. If unsure, ask; when in doubt, don't map the collection.
- **A2** ✅ Bidirectional: the `@ManyToOne` side owns the foreign key. Provide `addX`/`removeX` helpers that set
  both sides, and expose the collection read-only.
- **A3** ✅ Cascade only parent → child. Never cascade `REMOVE`/`ALL` on `@ManyToOne` or `@ManyToMany`.
- **M1** `jakarta.persistence.*` imports. **M2** `GenerationType.SEQUENCE`, unless the project already uses
  `IDENTITY` (which disables insert batching).

### House rules

- **F5** ✅ Lists, reports, exports and API responses use DTO projections (records), not entities. Load entities
  only when the use case modifies them.
- **S2** ✅ To apply changes from a form/request/DTO: `find` + setters on the managed entity, not `merge` of a
  client-built object (merge overwrites fields the client never sent). If you do merge, use the returned instance.
- **S4** Every unit of work runs in a transaction (`sessionFactory.inTransaction(s -> …)` /
  `fromTransaction(s -> …)`). Never share a `Session` between threads or requests. After any exception from
  Hibernate, roll back and discard the session; retry in a new one.
- **Q2** Use the query API the project already uses (JPA `createQuery` or Hibernate
  `createSelectionQuery`/`createMutationQuery`); don't mix them. Always pass the result type.

### Hibernate 7 specifics

- **S3** Removed in 7: `save`, `update`, `saveOrUpdate`, `delete`, `load(Class, id)`. Use `persist`, `merge`,
  `remove`, `getReference`/`find`. The legacy `Criteria` API is gone (use HQL or `CriteriaBuilder`).
- Record DTOs need no `select new`: `createSelectionQuery("select b.isbn, b.title from Book b", BookSummary.class)`.
- **F4** ✅ Pagination with a collection `join fetch` runs **in SQL from 7.4.0** (1 statement). **Up to 7.3** it
  loads all rows and pages in memory. Below 7.4 or in version-independent code, page the ids first, then fetch.
  Set `hibernate.query.fail_on_pagination_over_collection_fetch=true` where you control the configuration.
- **M5** ✅ Records work as `@Embeddable` and as DTOs, never as entities.
- **S5** ✅ Bulk work: `sessionFactory.inStatelessTransaction(s -> …)` with `s.insert(…)`. A regular session keeps
  every entity in memory, so use `flush()` + `clear()` every N there. `session.setJdbcBatchSize(50)` enables
  JDBC batching for one session without touching the configuration.

### General practice (details in the reference files)

- **F1** ✅ No lazy access in loops (N+1): `join fetch` what the use case needs, or **F2** ✅ an entity graph;
  **F3** ✅ `hibernate.default_batch_fetch_size` is a safety net, not a fix.
- **F6** ✅ Fix `LazyInitializationException` in the query. Never with EAGER, `enable_lazy_load_no_trans` or
  open-session-in-view.
- **A4** ✅ `Set` for `@ManyToMany`; never `join fetch` two `List` collections in one query.
  **A5** ✅ `orphanRemoval = true` for children that can't exist without their parent.
- **Q1** ✅ Always bind parameters; never concatenate values into queries.
  **Q4** ✅ Bulk `update`/`delete` bypasses loaded entities: run first, or `refresh`/`clear` after.
  **Q5** ✅ Never put a `where` on a join-fetched collection alias; filter with `exists`.
- **S1** ✅ Managed entities are saved automatically at commit; no `merge`/`persist` after setters.
