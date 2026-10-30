# Entity mapping

## Template

Start new entities from this shape:

```java
import jakarta.persistence.*;
import org.hibernate.annotations.NaturalId;

@Entity
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @NaturalId                                   // only if a real business key exists
    @Column(nullable = false, updatable = false)
    private String isbn;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Genre genre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private Author author;

    @Version
    private int version;

    protected Book() {
        // for Hibernate
    }

    public Book(String isbn, String title, Genre genre) {
        this.isbn = isbn;
        this.title = title;
        this.genre = genre;
    }

    // getters; setters only for fields that may change

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Book other)) return false;
        return isbn.equals(other.getIsbn());
    }

    @Override
    public int hashCode() {
        return isbn.hashCode();
    }

    @Override
    public String toString() {
        return "Book{id=" + id + ", isbn='" + isbn + "'}"; // no lazy associations
    }
}
```

## M1: Jakarta namespace

Hibernate 7 implements Jakarta Persistence 3.2. All mapping annotations come from `jakarta.persistence`.
Hibernate-specific annotations (`@NaturalId`, `@BatchSize`, `@JdbcTypeCode`, …) come from
`org.hibernate.annotations`. A `javax.persistence` import means the code was copied from a Hibernate ≤ 5
source: re-check it against these rules.

## M2: Identifiers

- Prefer `GenerationType.SEQUENCE`. Hibernate creates or expects a sequence named `<Entity>_SEQ` with
  increment 50 and assigns ids in memory from blocks of 50 (pooled optimizer).
- If you map the sequence explicitly, the database sequence's `INCREMENT BY` **must equal** `allocationSize`:
  ```java
  @SequenceGenerator(name = "book_seq", sequenceName = "book_seq", allocationSize = 50)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "book_seq")
  ```
- `IDENTITY` forces an immediate `INSERT` on `persist()` to learn the id, which disables JDBC insert batching.
  On MySQL/MariaDB and in SQL Server projects it is often the established convention. Follow the project's
  convention rather than mixing strategies. Where bulk inserts matter, use a `StatelessSession` or a SEQUENCE
  for that entity.
- Use `Long`/`UUID` wrapper types, not primitives: a `null` id means "not yet persisted".
- `UUID` ids: `@Id @GeneratedValue private UUID id;` works without extra configuration.

## M3: equals and hashCode ✅

Entities are put into `Set`s (collections, the persistence context, your own code). Their hash must not
change while they are in one.

**Option 1: natural id** (verified by `EntityMappingTest#naturalIdEquality`). If the entity has an immutable
business key (ISBN, email, order number), mark it `@NaturalId`, base `equals`/`hashCode` on it, and load by
it with `session.bySimpleNaturalId(Book.class).load(isbn)`.

**Option 2: generated id with constant hashCode** (verified by `EntityMappingTest#constantHashCodeSurvivesPersist`):

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Author other)) return false;
    return id != null && id.equals(other.getId());   // getter: 'other' may be a proxy
}

@Override
public int hashCode() {
    return Author.class.hashCode();                  // constant
}
```

**Wrong:** `Objects.hash(id)`. The hash changes from `null` to the generated value on persist, and the entity
is "lost" in any `HashSet` it was added to before (verified by `EntityMappingTest#idBasedHashCodeBreaksHashSet`).

**Proxies:** `session.getReference()` and lazy to-one associations return uninitialized proxies whose
**fields are null**. Always read the other object's state through getters, and use `instanceof`, not
`getClass() ==`, because the proxy is a subclass (verified by `EntityMappingTest#equalsWorksWithProxies`).

## M4: Enums ✅

```java
@Enumerated(EnumType.STRING)
private Genre genre;
```

Without it (or with a bare `@Enumerated`) Hibernate stores the ordinal. Inserting or reordering constants
then silently changes the meaning of existing rows (verified by `EntityMappingTest#ordinalEnumStoresPosition`).

## M5: Records ✅

- **As embeddables**: yes. Records are immutable value types, so replace the whole value to change it
  (verified by `EntityMappingTest#recordAsEmbeddable`):
  ```java
  @Embeddable
  public record Address(String street, String city, String country) {}

  @Embedded
  private Address address;
  ```
- **As DTO projections**: yes (see `fetching.md`, F5).
- **As entities**: no. Entities need a no-arg constructor, mutable state and must be proxyable.

## M6: Class shape ✅

- Non-`final` class, no `final` methods. Hibernate cannot proxy a `final` class, so a `@ManyToOne(fetch = LAZY)`
  pointing to it is **silently loaded eagerly**: 10 books → 11 statements instead of 1, despite `LAZY`
  (verified by `ClassShapeTest#finalEntityCannotBeLazy` vs. `ClassShapeTest#nonFinalEntityStaysLazy`).
- A `protected` no-arg constructor for Hibernate plus a public constructor that sets the required fields.
- Field access (annotations on fields) is the default. Don't mix it with getter annotations.
- Don't add setters for the id or `@Version` fields.
- Use `java.time` types (`LocalDate`, `Instant`, `OffsetDateTime`) directly. No `@Temporal`, no `java.util.Date`.
- Mark mandatory columns `@Column(nullable = false)` and mandatory to-one associations `optional = false`,
  so the schema enforces what the code assumes.

## M7: toString and Lombok ✅

`toString()` is called implicitly by loggers, debuggers, string concatenation and exception messages. If it
includes an association:
- inside the session, it **queries the database** just to log something (2 statements instead of 1,
  `ClassShapeTest#toStringWithLazyCollection`), and it can cascade through the whole object graph;
- outside the session, it **throws `LazyInitializationException`**, so a log statement crashes the request.

Include only the id and simple columns (`ClassShapeTest#toStringWithoutAssociations`).

Lombok:
- `@Data`, `@EqualsAndHashCode`, `@ToString` on entities: **no**. They include every field, including
  associations, and `@EqualsAndHashCode` hashes the generated id (M3).
- `@Getter`, `@Setter` (and `@NoArgsConstructor(access = AccessLevel.PROTECTED)`): fine.
- `@ToString(onlyExplicitlyIncluded = true)` with `@ToString.Include` on simple fields is acceptable if the
  project already uses Lombok throughout.
