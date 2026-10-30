<!-- GENERATED from skills/hibernate-orm/SKILL.md by tools/build_agents_md.py. Do not edit by hand. -->
# Hibernate ORM 7 rules for AI agents

Applies to all code using Hibernate ORM 7 / Jakarta Persistence 3.2 in this project.
Many Hibernate examples online target Hibernate 5 or older. When they contradict these rules, follow these rules.
`Session.save()`, `update()`, `saveOrUpdate()`, `delete()`, `load(Class, id)` and the legacy `Criteria` API
no longer exist.

Rules marked ✅ are verified by tests on Hibernate 7.0 through 7.4.
Full guidelines with examples: the `hibernate-orm` Agent Skill.

## Rules

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
