---
name: hibernate-orm
description: Rules and verified patterns for writing Hibernate ORM 7 / Jakarta Persistence 3.2 code in plain Java. Use when creating or changing JPA entities (@Entity, @Embeddable), associations (@OneToMany, @ManyToOne, @ManyToMany), HQL/JPQL/native queries, entity graphs, Session/EntityManager/StatelessSession usage, transactions, optimistic locking, or when diagnosing N+1 selects, LazyInitializationException or MultipleBagFetchException.
---

# Hibernate ORM 7 guidelines

Target: **Hibernate ORM 7.x** with **Jakarta Persistence 3.2**, plain Java (no framework assumptions).
Every rule tagged ✅ is backed by a test that passes on Hibernate 7.0 through 7.4 in the source repository.

Many Hibernate answers online describe Hibernate 5 or older. When something you remember contradicts
these rules, trust these rules: APIs such as `Session.save()`, `update()`, `saveOrUpdate()`, `delete()`,
`load(Class, id)` and the legacy `Criteria` API **no longer exist**.

## Workflow

1. **Before writing code**, check the project's Hibernate version (`pom.xml` / `build.gradle`). If it is
   below 7, the rules mostly still apply, but verify version-specific APIs before using them.
2. **Write the code** following the core rules below. For details, read the matching reference file:

   | Task | Read |
   |---|---|
   | Creating or changing an entity, id, equals/hashCode, enum, embeddable | [references/entity-mapping.md](references/entity-mapping.md) |
   | Adding or changing a relationship between entities | [references/associations.md](references/associations.md) |
   | Loading entity graphs, fixing N+1, pagination, DTOs | [references/fetching.md](references/fetching.md) |
   | Writing HQL/JPQL, native SQL, bulk updates | [references/querying.md](references/querying.md) |
   | Session/transaction handling, merge vs persist, batch jobs, locking | [references/sessions-and-transactions.md](references/sessions-and-transactions.md) |
   | Writing tests for persistence code | [references/testing.md](references/testing.md) |
   | Reviewing existing code | [references/anti-patterns.md](references/anti-patterns.md) |

3. **After writing code**, run the checker on the changed files and fix every ERROR. Fix WARNINGs unless
   you can state why the finding does not apply:
   ```bash
   python3 <skill-dir>/scripts/check_entities.py src/main/java
   ```
4. For code that loads data, **prove the statement count** with a test (see `references/testing.md`).
   A query that looks right can still issue N+1 selects.

## Core rules

### Mapping
- **M1** Import `jakarta.persistence.*`, never `javax.persistence.*`.
- **M2** Prefer `@GeneratedValue(strategy = GenerationType.SEQUENCE)` where the database supports sequences.
  If the project already uses `IDENTITY` (common on MySQL/SQL Server), follow the convention. But know that
  `IDENTITY` disables JDBC insert batching, so use a `StatelessSession` or SEQUENCE for bulk inserts.
- **M3** ✅ `equals`/`hashCode`: compare a **natural id** (`@NaturalId`) if one exists. Otherwise compare the
  generated id (non-null only) and return a **constant** `hashCode`. Never hash a generated id. Access the
  other object's state through **getters**, because it may be a proxy.
- **M4** ✅ Always `@Enumerated(EnumType.STRING)`. The default `ORDINAL` breaks when constants are reordered.
- **M5** ✅ Records are fine as `@Embeddable` values and DTO projections, never as entities.
- **M6** ✅ Entities are non-`final` classes with a `protected` no-arg constructor and no `final` methods.
  Hibernate can't proxy a `final` class, so `LAZY` associations pointing to it are silently loaded eagerly.
- **M7** ✅ `toString()` must not touch associations. It would query inside the session and throw
  `LazyInitializationException` outside. Never use Lombok `@Data`, `@EqualsAndHashCode` or `@ToString` on
  entities. `@Getter`/`@Setter` are fine.

### Associations
- **A1** ✅ Every `@ManyToOne` and `@OneToOne` gets `fetch = FetchType.LAZY` (the default is EAGER). Never use
  `FetchType.EAGER`. Decide fetching per query instead.
- **A2** ✅ In a bidirectional association, the `@ManyToOne` side owns the foreign key. The `mappedBy` side
  is ignored on write. Provide `addX`/`removeX` helper methods that set **both** sides, and expose the
  collection read-only.
- **A3** ✅ Cascade only from parent to child (`@OneToMany(cascade = ALL, orphanRemoval = true)`). Never cascade
  `REMOVE`/`ALL` on `@ManyToOne` or `@ManyToMany`: deleting one child would delete the shared parent and,
  through the parent's cascades, all its other children.
- **A4** ✅ Use `Set` for `@ManyToMany`. Never join fetch two `List` collections in one query
  (`MultipleBagFetchException`). Use one query per collection instead.
- **A5** ✅ Use `orphanRemoval = true` when a child cannot exist without its parent.
- **A6** ✅ Map a `@OneToMany` collection only if it is **bounded by the domain** (order lines, invoice
  positions). Children that accumulate over time (orders, events, transactions, reviews) are never mapped as a
  collection: keep the `@ManyToOne` on the child and load them with a paged query. Touching a mapped collection
  loads **every** element. If neither the domain nor existing code (paging, counts) tells you, ask; when in
  doubt, don't map the collection.

### Fetching
- **F1** ✅ Accessing a lazy association inside a loop is an N+1. Fetch what the use case needs up front with
  `join fetch`.
- **F2** ✅ Or use an entity graph: `query.setEntityGraph(graph, GraphSemantic.FETCH)`.
- **F3** ✅ Set `hibernate.default_batch_fetch_size` (e.g. 16–64) as a safety net. It turns N+1 into
  1 + N/size, but it is not a substitute for F1/F2.
- **F4** ✅ Pagination with a collection `join fetch` depends on the version. From **7.4.0** on, Hibernate
  pages the root entities in SQL (1 statement). **Up to 7.3** it loads *all* rows and pages in memory.
  Always set `hibernate.query.fail_on_pagination_over_collection_fetch=true` so that in-memory pagination
  throws. Below 7.4, or in version-independent code, page the ids first and then fetch them.
- **F5** ✅ Lists, reports, exports and API responses **always** use DTO projections (records), never
  entities: `createSelectionQuery("select b.isbn, b.title from Book b", BookSummary.class)`. Load entities
  only when the use case modifies them.
- **F6** ✅ Load everything you need **inside** the session/transaction. Never "fix" a
  `LazyInitializationException` with EAGER, `hibernate.enable_lazy_load_no_trans` or open-session-in-view.
  Fix the query instead.

### Querying
- **Q1** ✅ Always bind parameters (`:name` + `setParameter`). Never concatenate or `String.format` values into
  HQL/SQL.
- **Q2** Follow the query API the project already uses: JPA (`EntityManager.createQuery(jpql, Type.class)`)
  or Hibernate (`Session.createSelectionQuery(hql, Type.class)` for reads, `createMutationQuery(hql)` for
  writes). Don't mix them. Without an existing convention, use the Hibernate APIs. Always pass the result
  type; never use raw `Query`. The legacy `org.hibernate.Criteria` is gone; use HQL or `CriteriaBuilder`.
- **Q4** ✅ Bulk `update`/`delete` queries bypass the persistence context, cascades and callbacks. Run them
  before loading entities, or `refresh()`/`clear()` afterwards.
- **Q5** ✅ Never restrict a join-fetched collection in `where`. The managed entity would hold a partial
  collection. Filter with `exists`/a second join, or select DTOs.

### Sessions and transactions
- **S1** ✅ Managed entities are saved automatically at flush (dirty checking). Don't call `merge`/`persist`
  on entities that are already managed.
- **S2** ✅ `persist(new)` makes the passed instance managed. `merge(detached)` returns a **different**,
  managed instance: use the return value. To apply changes from outside (request, DTO), prefer
  **load + change the managed entity** over merging a client-supplied object. `merge` copies *all* fields,
  including ones the client never sent or must not change.
- **S3** `save`, `update`, `saveOrUpdate`, `delete`, `load(Class, id)` were removed in Hibernate 7. Use
  `persist`, `merge`, `remove`, `getReference` and `find`.
- **S4** Every read and write runs in a transaction: `sessionFactory.inTransaction(s -> ...)` /
  `fromTransaction(s -> ...)`. Build one `SessionFactory` per application. A `Session` is short-lived,
  not thread-safe, and spans one unit of work. Never store it in a field shared between threads or requests.
  **After any exception from Hibernate, roll back and discard the session.** Its state is undefined, so
  never continue using it or its entities.
- **S5** ✅ For batch jobs, use a `StatelessSession` (`inStatelessTransaction`), or `flush()` + `clear()`
  every `hibernate.jdbc.batch_size` entities. A regular session keeps every persisted entity in memory
  until it ends.
- **C1** ✅ Put `@Version` on **every entity that is modified after creation**. Only insert-only/immutable
  entities may skip it. Never set the version yourself. A stale update throws `OptimisticLockException`.
