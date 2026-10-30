# Sessions, transactions and concurrency

## Lifecycle in one picture

```
new Book(...)  --persist()-->  MANAGED  --commit/close-->  DETACHED  --merge()-->  (returns a MANAGED copy)
                                  |
                              remove()
                                  v
                               REMOVED  --flush-->  row deleted
```

- **Managed** entities are tracked by the session (the persistence context / first-level cache). At flush
  (before commit and before queries that touch the same tables), Hibernate compares their state with the
  snapshot and writes the differences.
- **Detached** entities were managed by a session that is now closed or cleared. Changing them does nothing
  until they are merged.

## S1: Dirty checking ✅

```java
sessionFactory.inTransaction(session -> {
    var author = session.find(Author.class, id);
    author.setName("New name");      // that's all; UPDATE is issued at commit
});
```

Verified by `SessionLifecycleTest#dirtyCheckingWritesChanges`. Calling `merge()` or `persist()` on an
already-managed entity is noise at best, and misleads readers into thinking it's required.

## S2: persist vs. merge ✅

| Method | Input | Effect | Use |
|---|---|---|---|
| `persist(e)` | new (transient) instance | `e` becomes managed, INSERT at flush | creating entities |
| `merge(e)` | detached instance (or new) | copies state onto a managed instance and **returns it**; `e` stays detached | applying changes from outside the session (e.g. a deserialized object) |
| `remove(e)` | managed instance | DELETE at flush | deleting |
| `find(Class, id)` | id | loads (or returns the cached instance), `null` if absent | reading |
| `getReference(Class, id)` | id | uninitialized proxy, no SELECT | setting a foreign key: `book.setPublisher(session.getReference(Publisher.class, id))` |

```java
var managed = session.merge(detached);   // continue working with 'managed', not 'detached'
```

Verified by `SessionLifecycleTest#mergeReturnsManagedCopy`. `persist()` on a detached instance throws
("detached entity passed to persist").

**Prefer load + change over merge** for changes coming from outside (HTTP request, message, DTO):

```java
// preferred: only the fields this use case may change are touched
sessionFactory.inTransaction(session -> {
    var book = session.find(Book.class, request.id());
    book.setTitle(request.title());
});

// avoid: merge copies ALL fields of 'bookFromClient' onto the managed entity:
// fields the client didn't send become null, and fields it must not change get overwritten
session.merge(bookFromClient);
```

Use `merge` for detached entities your own code loaded earlier (e.g. in a previous transaction), not for
objects assembled from client input.

## S3: Removed APIs (Hibernate 7)

| Removed | Use instead |
|---|---|
| `session.save(e)` | `session.persist(e)` |
| `session.saveOrUpdate(e)` | `persist(e)` for new, `merge(e)` for detached |
| `session.update(e)` | `merge(e)`, or load + modify |
| `session.delete(e)` | `session.remove(e)` |
| `session.load(Class, id)` | `getReference(Class, id)` (proxy) or `find(Class, id)` |
| `session.createCriteria(...)` | HQL or `CriteriaBuilder` |

Verified by inspecting the Hibernate 7.4 `Session` interface: these methods do not exist.

## S4: Transactions and session scope

```java
SessionFactory sessionFactory = ...;                  // one per application, thread-safe, expensive

sessionFactory.inTransaction(session -> { ... });     // void unit of work
var result = sessionFactory.fromTransaction(session -> ...);   // unit of work returning a value
```

These helpers open a session, begin a transaction, commit (or roll back on exception) and close.

- Reads run in transactions too: consistent snapshot, and lazy loading works within the unit of work.
- One session per unit of work (e.g. per request or per message). Never store a `Session`/`EntityManager`
  in a field shared between threads; it is not thread-safe.
- Don't keep a transaction open while waiting for a user or a remote call. Load, release, and use optimistic
  locking (C1) when writing back later.
- **After an exception from Hibernate, roll back and discard the session.** The persistence context may
  hold entities whose state doesn't match the database (half-applied flush, generated ids for rolled-back
  inserts). Don't catch the exception and keep using the session, and don't reuse its entities in a new session
  without reloading them. `inTransaction`/`fromTransaction` already roll back and close. To retry (e.g. after
  `OptimisticLockException`), run the whole unit of work again in a new session.
- Bootstrap in plain Java (Hibernate 7):
  ```java
  SessionFactory sessionFactory = new HibernatePersistenceConfiguration("app")
          .managedClasses(Author.class, Book.class, Publisher.class)
          .jdbcUrl(url)
          .jdbcCredentials(user, password)
          .property(FetchSettings.DEFAULT_BATCH_FETCH_SIZE, 16)
          .property(QuerySettings.FAIL_ON_PAGINATION_OVER_COLLECTION_FETCH, true)
          .createEntityManagerFactory();
  ```
  In production, configure a real connection pool (e.g. HikariCP via `hibernate-hikaricp`). Validate the
  schema (`hibernate.hbm2ddl.auto=validate`) and manage it with a migration tool, never `update`.

## S5: Batch processing ✅

For imports/exports of many rows, a regular session keeps every entity in memory and dirty-checks all of them
at each flush. After 500 `persist()` calls, the session holds 500 entities. With `flush()` + `clear()` every 50
it never holds more than 50 (`SessionLifecycleTest#regularSessionKeepsEveryEntity`,
`SessionLifecycleTest#flushAndClearBoundsMemory`). Two options:

**StatelessSession**: no persistence context, no dirty checking, no cascades, no lazy loading. Every call is
one statement (`SessionLifecycleTest#statelessSessionForBulkInsert`):
```java
sessionFactory.inStatelessTransaction(session -> {
    for (var row : rows) {
        session.insert(toEntity(row));
    }
});
```
Use `insert`, `update`, `delete`, `upsert`; associations are fetched explicitly with `session.fetch(...)` or
`join fetch`.

If the application doesn't set `hibernate.jdbc.batch_size`, enable batching for just this session with
`session.setJdbcBatchSize(50)`. 1,000 inserts then need less than a tenth of the statements
(`SessionLifecycleTest#statelessSessionBatchSize`).

**Regular session with flush/clear**, when you need cascades or callbacks:
```java
// hibernate.jdbc.batch_size=50, hibernate.order_inserts=true, hibernate.order_updates=true
sessionFactory.inTransaction(session -> {
    for (int i = 0; i < rows.size(); i++) {
        session.persist(toEntity(rows.get(i)));
        if ((i + 1) % 50 == 0) {
            session.flush();
            session.clear();
        }
    }
});
```
JDBC batching requires `hibernate.jdbc.batch_size` and **SEQUENCE ids** (M2). `IDENTITY` silently disables
insert batching.

## C1: Optimistic locking ✅

```java
@Version
private int version;      // or long / Instant; never set it yourself
```

Put it on **every entity that is modified after creation**. Don't try to judge whether concurrent edits are
"likely": two browser tabs, a retried request or a background job are enough to lose an update silently.
It costs one column and a `where version = ?` per update. Only insert-only or immutable entities (log entries,
events, `@Immutable` reference data) may skip it.

Each update runs `... where id = ? and version = ?` and increments the version. If another transaction changed
the row in between, the update affects 0 rows and Hibernate throws `jakarta.persistence.OptimisticLockException`
(`SessionLifecycleTest#optimisticLockingRejectsStaleMerge`). Handle it at the use-case level: reload and retry,
or report a conflict to the user.

Use pessimistic locks (`session.find(Book.class, id, LockModeType.PESSIMISTIC_WRITE)`) only for short,
high-contention critical sections, such as counters or reservations.

## Second-level cache

Off by default. Enable it only for read-mostly reference data, per entity (`@Cache`), with a JCache provider,
and after measuring. It does not fix N+1 problems and adds staleness issues in clustered setups.
