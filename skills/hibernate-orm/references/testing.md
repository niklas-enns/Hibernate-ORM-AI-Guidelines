# Testing persistence code

A test that only checks results won't notice an N+1: the data is correct, it's just 101 queries. Assert the
**number of statements** for every use case that loads data.

## Count statements with Hibernate statistics

```java
// configuration
.property(StatisticsSettings.GENERATE_STATISTICS, true)

// helper
long statementsDuring(Runnable action) {
    var statistics = sessionFactory.getStatistics();
    statistics.clear();
    action.run();
    return statistics.getPrepareStatementCount();
}

// test
var statements = statementsDuring(() -> sessionFactory.inTransaction(session ->
        service.loadAuthorsWithBooks(session)));
assertThat(statements).isEqualTo(1);
```

Create test data **before** measuring: inserts and sequence calls are counted too. Use enough rows (e.g. 10
parents) that an N+1 is unmistakable (11 instead of 1).

## Database

- Fast feedback: H2 in-memory with a unique URL per test (`jdbc:h2:mem:<uuid>;DB_CLOSE_DELAY=-1`) and
  `schemaToolingAction(Action.CREATE_DROP)`.
- Before relying on vendor-specific behavior (native SQL, locking, JSON, sequences), test against the real
  database with Testcontainers.

## Seeing the SQL

- Quick look: `hibernate.show_sql=true` and `hibernate.format_sql=true`.
- Proper logging: logger `org.hibernate.SQL` at DEBUG for statements, `org.hibernate.orm.jdbc.bind` at TRACE
  for bound parameter values.
- Never assert on exact SQL text: it changes between Hibernate versions and dialects. Assert counts and results.

## What to test

- **Mappings**: persist, clear/close the session, reload in a new session, compare. Without a fresh session you
  only test the first-level cache.
- **Fetching**: statement count per use case, and that the needed associations are initialized
  (`Hibernate.isInitialized(...)`).
- **Writes**: reload in a new session and check the data, including foreign keys (catches A2 mistakes).
- **Concurrency**: two sessions editing the same row → `OptimisticLockException`.
