# Anti-pattern checklist

Use this when reviewing or changing existing Hibernate code. The `Checker` column names the
`scripts/check_entities.py` rule that detects it automatically (`–` = review manually).

| # | Anti-pattern | Why it hurts | Fix | Rule | Checker |
|---|---|---|---|---|---|
| 1 | `import javax.persistence.*` | not supported by Hibernate 7; code copied from old sources | `jakarta.persistence.*` | M1 | HIB001 |
| 2 | `FetchType.EAGER` anywhere | loaded on every query, can't be turned off, N+1 | LAZY + fetch per use case | A1 | HIB002 |
| 3 | `@ManyToOne`/`@OneToOne` without `fetch = LAZY` | EAGER by default → N+1 even if unused | `fetch = FetchType.LAZY` | A1 | HIB003 |
| 4 | bare `@Enumerated` / `ORDINAL` | reordering constants corrupts data | `EnumType.STRING` | M4 | HIB004 |
| 5 | `save`, `saveOrUpdate`, `update`, `delete`, `load(Class,id)`, `createCriteria` | removed in Hibernate 6/7 | `persist`, `merge`, `remove`, `getReference`/`find`, HQL | S3 | HIB005 (partly) |
| 6 | query built with `+` or `String.format` | injection, no plan cache | `:param` + `setParameter` | Q1 | HIB006 |
| 7 | `cascade = ALL/REMOVE` on `@ManyToOne` / `@ManyToMany` | deletes shared entities | cascade parent → child only | A3 | HIB007 |
| 8 | `hashCode()` from generated id | entity lost in `HashSet` after persist | natural id or constant hash | M3 | HIB008 |
| 9 | Lombok `@Data`/`@EqualsAndHashCode`/`@ToString` on entity | lazy loading in `toString`, broken hash | hand-written methods, or `@Getter`/`@Setter` only | M7 | HIB009 |
| 10 | `GenerationType.IDENTITY` where sequences are available | no insert batching | `SEQUENCE`, or follow the project's existing convention | M2 | HIB010 (info) |
| 11 | `@ManyToMany List<...>` | rewrites the whole join table on change | `Set` | A4 | HIB011 |
| 12 | `final` entity class/methods | no proxies → LAZY to-one silently loaded eagerly | non-final | M6 | HIB012 |
| 13 | `hibernate.enable_lazy_load_no_trans=true` | hidden N+1, inconsistent reads | fetch in the transaction | F6 | HIB013 |
| 14 | lazy getter inside a loop / stream / `toString` | N+1 | `join fetch`, entity graph, DTO | F1 | – |
| 15 | only `parent.getChildren().add(child)` | foreign key never written | helper method setting both sides | A2 | – |
| 16 | `join fetch` of two `List`s | `MultipleBagFetchException` | one query per collection | A4 | – |
| 17 | `where` on a join-fetched collection alias | partial collection in managed entity | `exists` subquery | Q5 | HIB014 |
| 18 | `distinct` added to `join fetch` queries | unnecessary since Hibernate 6 | remove it | Q3 | – |
| 19 | entities loaded after a bulk `update`/`delete` used as if current | stale state | refresh/clear, or bulk first | Q4 | – |
| 20 | `session.merge(x)` result ignored, `x` used further | `x` is still detached | use the returned instance | S2 | – |
| 21 | explicit `merge`/`update` call for managed entity | not needed, misleading | rely on dirty checking | S1 | – |
| 22 | entities returned from the service layer and serialized as JSON | lazy loading during serialization, exposes schema | DTO projection | F5 | – |
| 23 | one session/EntityManager shared across threads or requests | not thread-safe, unbounded memory | one session per unit of work | S4 | – |
| 24 | thousands of entities in one regular session | memory + slow flushes | `StatelessSession` or flush/clear | S5 | – |
| 25 | `hbm2ddl.auto=update` in production | unreviewed schema changes | migration tool + `validate` | S4 | – |
| 26 | `toString()` including associations | queries just for logging, throws outside session | id + simple columns only | M7 | – |
| 27 | `@OneToMany` for a collection that grows over time (orders, events, …) | first access loads all rows | `@ManyToOne` on child + paged query | A6 | – |
| 28 | redundant `merge`/`persist` after setters | misleading; `merge` of client objects overwrites unsent fields | load + change | S1/S2 | – |
| 29 | session reused after an exception | undefined state, corrupt data | roll back, discard, retry in a new session | S4 | – |
| 30 | entity modified after creation without `@Version` | lost updates | add `@Version` | C1 | – |
