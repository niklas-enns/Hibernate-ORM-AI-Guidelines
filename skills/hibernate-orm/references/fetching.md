# Fetching

Map every association LAZY (A1), then decide **per use case** what to load. Each use case should
need a small, constant number of statements, independent of the number of rows.

## Choosing a strategy

| Situation | Use |
|---|---|
| Use case needs entities + specific associations, will modify them | `join fetch` (F1) or entity graph (F2) |
| Same query, different callers need different associations | entity graph (F2) |
| Read-only list/table/export/API response | DTO projection, always (F5) |
| Generic code path you can't tune per query | `default_batch_fetch_size` as safety net (F3) |
| Paged list of parents with their children | `join fetch` + `setPage` from 7.4.0, two-step ids before (F4) |

## F1: N+1 and join fetch ✅

```java
// N+1: 1 query for authors + 1 per author for its books
for (Author author : session.createSelectionQuery("from Author", Author.class).getResultList()) {
    author.getBooks().size();
}

// 1 query
var authors = session.createSelectionQuery("from Author a join fetch a.books", Author.class)
        .getResultList();
```

10 authors: 11 statements vs. 1 (verified by `FetchingTest#lazyCollectionInLoopCausesNPlusOne` and
`FetchingTest#joinFetchLoadsInOneSelect`). Use `left join fetch` if parents without children must be in the result.

Signs of an N+1: a lazy getter called inside a loop, a stream `map`, a `toString()`, or a JSON serializer
walking entities.

## F2: Entity graphs ✅

```java
var graph = session.createEntityGraph(Author.class);
graph.addAttributeNodes("books");
var authors = session.createSelectionQuery("from Author", Author.class)
        .setEntityGraph(graph, GraphSemantic.FETCH)
        .getResultList();
```

One statement, collections initialized (`FetchingTest#entityGraphFetchesCollection`). `GraphSemantic.FETCH`
treats attributes not in the graph as LAZY. `GraphSemantic.LOAD` keeps the mapping defaults for them. Named
graphs (`@NamedEntityGraph` on the entity) work with `session.getEntityGraph("name")`.

## F3: Batch fetching as a safety net ✅

```properties
hibernate.default_batch_fetch_size=16
```

or per association/entity: `@BatchSize(size = 16)`. Lazy loads then initialize up to 16 pending proxies or
collections with one `IN`/array query: 10 authors take **2** statements instead of 11
(`BatchFetchingTest#batchFetchingLimitsNPlusOne`). That still makes extra round trips, so prefer F1/F2 where
you know the use case.

## F4: Pagination ✅

`setPage`/`setMaxResults` combined with a collection `join fetch` behaves differently depending on the
version (verified by `FetchingTest#paginationWithCollectionFetch` on 7.0.10, 7.1.36, 7.2.25, 7.3.13, 7.4.0
and 7.4.14):

| Version | Behavior |
|---|---|
| **7.4.0 and later** | Pages the **root entities in SQL**. Hibernate wraps the root in a paged derived table and joins the collection to it. 1 statement, complete collections. |
| **up to 7.3.x** (and Hibernate 6) | Loads **all** matching rows and pages **in memory**, logging only a warning (`HHH90003004`). |

```java
// 7.4.0+: one statement
var page = session.createSelectionQuery("from Author a join fetch a.books order by a.id", Author.class)
        .setPage(Page.first(20))
        .getResultList();
```

Always set

```properties
hibernate.query.fail_on_pagination_over_collection_fetch=true
```

Below 7.4 this turns in-memory pagination into a `HibernateException`, instead of silently loading the
whole table. From 7.4 on it guards against falling back to in-memory pagination.

Below 7.4, or in code that must work on every version, page the ids first (2 statements,
`FetchingTest#paginateIdsThenFetch`):

```java
var ids = session.createSelectionQuery("select a.id from Author a order by a.id", Long.class)
        .setPage(Page.first(20)).getResultList();
var page = session.createSelectionQuery(
                "from Author a join fetch a.books where a.id in :ids order by a.id", Author.class)
        .setParameter("ids", ids).getResultList();
```

Always `order by` a unique column when paging.

## F5: DTO projections ✅

Lists, reports, exports and API responses **always** use DTO projections, never entities. Load entities only
when the use case modifies them. Returning entities from a service layer leads to lazy loading during
serialization, `LazyInitializationException`, over-fetching, and exposes the schema to clients.

Select straight into a record. Hibernate 7 matches the select list to the record's constructor, no
`select new` needed (`FetchingTest#recordProjection`):

```java
public record BookSummary(String isbn, String title, String authorName) {}

List<BookSummary> rows = session.createSelectionQuery(
        "select b.isbn, b.title, b.author.name from Book b order by b.isbn", BookSummary.class)
    .getResultList();
```

DTOs aren't managed: no dirty checking, no lazy loading, no `LazyInitializationException`, and only the needed
columns are read. Use them for lists, reports and API responses.

## F6: LazyInitializationException ✅

Thrown when a lazy association is touched after its session has closed
(`FetchingTest#lazyAccessOutsideSessionFails`). Fix: fetch it in the query that loads the entity (F1, F2), or
return a DTO (F5).

Never fix it with:
- `FetchType.EAGER`: loads the association everywhere, creating N+1s in other places.
- `hibernate.enable_lazy_load_no_trans=true`: opens a new session and connection per lazy access. That's a
  hidden N+1, and the data isn't consistent across the reads.
- `Hibernate.initialize()` calls in a loop: that is the N+1, just moved.
- Keeping the session open through view rendering ("open session in view"): moves queries into the view layer,
  where nobody counts them.

`Hibernate.initialize(entity.getX())` is fine for a **single** entity inside the session.
`Hibernate.isInitialized(x)` checks without loading.
