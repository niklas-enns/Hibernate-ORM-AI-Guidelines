# Querying

## Which API

| Need | Use |
|---|---|
| Load by id | `session.find(Book.class, id)` |
| Reference for setting a foreign key, without loading | `session.getReference(Author.class, id)` |
| Load by natural id | `session.bySimpleNaturalId(Book.class).load(isbn)` |
| Select entities/DTOs/scalars | `session.createSelectionQuery(hql, Type.class)` |
| Bulk update/delete | `session.createMutationQuery(hql).executeUpdate()` |
| Dynamic filters built at runtime | JPA `CriteriaBuilder` (`session.getCriteriaBuilder()`) |
| Database-specific SQL | `session.createNativeQuery(sql, Type.class)` |
| Reused query | `@NamedQuery` on the entity (validated at startup) |

With plain JPA, the equivalents are `entityManager.createQuery(jpql, Type.class)` and
`entityManager.find(...)`. In Hibernate 7, `Session` **is** an `EntityManager`
(`entityManager.unwrap(Session.class)` gives access to the Hibernate-specific APIs).

**Q2: Stay consistent with the project.** Before writing a query, check which API the surrounding code uses
(`EntityManager` + `createQuery`, or `Session` + `createSelectionQuery`/`createMutationQuery`) and use the same.
Mixing both in one codebase makes code harder to read and review. Use the Hibernate-specific API only where JPA
has no equivalent (natural ids, `StatelessSession`, `Page`, entity graphs with `GraphSemantic`), reached via
`unwrap(Session.class)`. In a new project without a convention, use the Hibernate APIs.

Removed APIs you must not generate: `org.hibernate.Criteria` / `session.createCriteria()` (removed in 6),
`Query.list()` with raw types, `session.createSQLQuery()` (use `createNativeQuery`).

## Q1: Parameters ✅

```java
// correct
session.createSelectionQuery("from Author where name = :name", Author.class)
        .setParameter("name", name)
        .getResultList();

// WRONG: HQL/SQL injection, and every distinct string is a new query plan
session.createSelectionQuery("from Author where name = '" + name + "'", Author.class);
```

Bound values are never interpreted as query syntax: `O'Reilly` matches, `x' or '1'='1` matches nothing
(`QueryingTest#parametersAreNotInterpreted`). For collections: `where a.id in :ids` with
`setParameter("ids", list)`. Identifiers (sort column, entity name) can't be parameters. Map them from an
allow-list (`switch` over an enum), never from raw input.

## Q2: HQL style

- Write `from Book b where ...`. In Hibernate 7, `select b` is optional for single-entity queries.
- Navigate to-one associations with paths (`b.author.name`), which creates an implicit inner join. Use an
  explicit `left join` if nulls must survive.
- Use `count(*)`, `exists(...)` and `select ... limit` instead of loading entities to count or check
  existence.
- `getSingleResult()` throws if there is no row. Use `getSingleResultOrNull()` or `uniqueResultOptional()` when
  absence is valid.
- Use `Page.first(size)` / `page.next()` with `setPage()`, or `setFirstResult`/`setMaxResults`. Always with
  `order by`.

## Q3: No DISTINCT for join fetch ✅ (anti-pattern list only)

Since Hibernate 6, entity results of `join fetch` are de-duplicated in memory automatically. 2 authors with 3
books each return 2 `Author` objects (`QueryingTest#joinFetchDeduplicatesRoots`). Adding `distinct` only
adds a `DISTINCT` to the SQL, which costs time and fails on some column types.

## Q4: Bulk operations ✅

`update`/`delete` HQL statements run directly in the database. They **bypass the persistence context**: entities
already loaded in the session keep their old state (`QueryingTest#bulkUpdateBypassesPersistenceContext`). They
also skip cascades, `orphanRemoval`, entity callbacks and `@Version` increments (unless written as
`update versioned Book set ...`).

- Run bulk operations in their own transaction, or before loading the affected entities.
- Afterwards, `session.refresh(entity)` or `session.clear()` if the session holds affected entities.
- Delete children before parents yourself: bulk `delete` doesn't cascade.

## Q5: Never filter a fetched collection ✅

```java
// WRONG: author.getBooks() now contains only the matching book, as if the others didn't exist
"from Author a join fetch a.books b where b.isbn = :isbn"

// correct: filter with exists (or a second, non-fetch join), fetch the complete collection
"from Author a join fetch a.books where exists (select 1 from Book b where b.author = a and b.isbn = :isbn)"
```

A join-fetched collection becomes the entity's managed state. Restricting it gives the session a partial
collection (`QueryingTest#filteringFetchedCollectionCorruptsState`). With `orphanRemoval` or later writes, that
partial view can change the data. If you only need the matching children, query the children (or DTOs) instead.

## Native SQL

- Return entities only if the query selects **all** mapped columns; otherwise use a DTO/record or `Object[]`.
- Hibernate can't parse which tables a native query reads. If it must see pending changes from the
  same session, declare them with `addSynchronizedEntityClass(Book.class)` or call `session.flush()` first.
- Keep native queries for things HQL can't express (vendor-specific functions, hints, features).
- Bind parameters exactly as in Q1.
