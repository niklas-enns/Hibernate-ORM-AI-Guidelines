# Associations

## Decision table

| Relationship | Mapping |
|---|---|
| Child → parent (most common) | `@ManyToOne(fetch = LAZY)` on the child, **owning side** |
| Parent → children, needed for navigation/cascade | add `@OneToMany(mappedBy = "parent")` on the parent |
| Many-to-many without extra columns | `@ManyToMany` with `Set`, owning side has `@JoinTable` |
| Many-to-many **with** extra columns (date, role, …) | map the join table as its own entity with two `@ManyToOne`s |
| One-to-one sharing the primary key | `@OneToOne(fetch = LAZY) @MapsId` on the dependent side, unidirectional |

Start with the unidirectional `@ManyToOne`. Add the inverse `@OneToMany` only if the code really
navigates from parent to children or needs cascading, **and** the collection is bounded (A6).

## A1: LAZY to-one associations ✅

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "author_id")
private Author author;
```

`@ManyToOne` and `@OneToOne` are **EAGER by default**. An EAGER association is loaded for every query
result, even if the code never touches it. A plain `from EagerBook` over 10 books with 10 distinct authors
runs **11** statements (verified by `FetchingTest#eagerToOneCausesNPlusOne`). With `LAZY` it runs **1**
(`FetchingTest#lazyToOneLoadsNothingExtra`). EAGER cannot be turned off per query; LAZY can be overridden
per query with `join fetch` or an entity graph.

The inverse side of a bidirectional `@OneToOne(mappedBy = ...)` cannot be lazy without bytecode
enhancement. Prefer a unidirectional `@OneToOne @MapsId` from the dependent entity.

## A2: Bidirectional one-to-many ✅

```java
// Parent
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
private Set<Book> books = new HashSet<>();

public void addBook(Book book) {
    books.add(book);
    book.setAuthor(this);
}

public void removeBook(Book book) {
    books.remove(book);
    book.setAuthor(null);
}

public Set<Book> getBooks() {
    return Collections.unmodifiableSet(books);
}

// Child (owning side: holds the foreign key)
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "author_id")
private Author author;

void setAuthor(Author author) {   // package-private: callers use Author.addBook()
    this.author = author;
}
```

Hibernate writes the foreign key **only from the owning side** (the side without `mappedBy`). Adding a child
only to the parent's collection persists nothing (verified by `AssociationTest#inverseSideOnlyIsIgnored`). The
helper methods keep both sides consistent (`AssociationTest#helperMethodSetsOwningSide`). Expose the
collection read-only so callers can't bypass them.

Initialize collection fields (`= new HashSet<>()`). Never replace the collection instance in a setter:
Hibernate tracks the instance it gave you.

## A3: Cascades ✅

- `cascade = CascadeType.ALL` belongs on a `@OneToMany`/`@OneToOne` from an aggregate root to children it
  **exclusively owns**.
- Never cascade `REMOVE` or `ALL` from child to parent (`@ManyToOne`). With
  `@ManyToOne(cascade = ALL) Author author` on a `Review`, deleting **one review** deletes the author, and through
  `Author.books` (cascade ALL) **all of the author's books** (verified by
  `AssociationTest#cascadeFromChildToParentDeletesSharedParent`). Cascades chain: always check what the target
  entity cascades further.
- Never cascade `REMOVE` on `@ManyToMany`: removing a student would delete courses other students attend.
  `PERSIST`/`MERGE` at most.
- `CascadeType.REMOVE` deletes children when the parent is deleted. `orphanRemoval = true` additionally deletes
  a child as soon as it is removed from the collection.

## A4: Collections types and multiple collections ✅

- `List` without `@OrderColumn` is a **bag**. Hibernate cannot identify individual rows in a `@ManyToMany`
  bag, so any change deletes and re-inserts all join-table rows. Use `Set` for `@ManyToMany`.
- Join fetching **two bags in one query** fails with `MultipleBagFetchException`
  (`AssociationTest#twoBagsCannotBeFetchedTogether`). Don't switch to `Set` just to make that query work: a
  join over two collections produces a Cartesian product (rows = a × b). Load each collection with its own
  query in the same session instead: the second query fills the already-managed entities
  (`AssociationTest#fetchCollectionsInSeparateQueries`):
  ```java
  var authors = session.createSelectionQuery(
          "from Author a left join fetch a.books where a.id in :ids", Author.class)
      .setParameter("ids", ids).getResultList();
  session.createSelectionQuery(
          "from Author a left join fetch a.awards where a in :authors", Author.class)
      .setParameter("authors", authors).getResultList();
  ```
- If order matters, prefer `@OrderBy("title")` on a `Set` (backed by a `LinkedHashSet`) or sort in the query.

## A5: orphanRemoval ✅

With `orphanRemoval = true`, `author.removeBook(book)` deletes the book row at flush. No explicit
`session.remove(book)` is needed (`AssociationTest#orphanRemovalDeletesChild`). Only use it when the child has
no meaning without the parent.

## A6: Map only bounded collections ✅

Whether a collection is "large" can't be read from the code. Decide by asking: **is it bounded by the domain,
or does it grow with time and usage?**

| Bounded: may be mapped | Unbounded: never map, query instead |
|---|---|
| `Order → OrderLines` | `Customer → Orders` |
| `Invoice → Positions` | `User → LoginEvents` |
| `Recipe → Ingredients` | `Product → Reviews` |
| `Survey → Questions` | `Account → Transactions` |

Rule of thumb: if the child is an event, log entry, history entry, transaction, or something created per user
action, it is unbounded.

Signals in existing code that a relationship is large: it is already queried with paging (`setPage`,
`setMaxResults`, a `Pageable`), there are `count(*)` queries on it, or migrations add indexes for it. If neither
the domain nor the code tells you, **ask**. The costs are asymmetric: an unmapped collection costs one query
method, while a wrongly mapped unbounded one loads 100,000 rows the first time someone calls the getter.

Touching a mapped collection (`size()`, iteration, `contains()`) loads **every** element into the session. For
1,000 books, the session then manages 1,001 entities. A count plus a paged query manages 20
(`AssociationTest#mappedCollectionLoadsEverything`):

```java
// unbounded relationship: no @OneToMany on Customer; Order has @ManyToOne(fetch = LAZY) Customer customer
long total = session.createSelectionQuery(
        "select count(*) from Order o where o.customer.id = :id", Long.class)
    .setParameter("id", customerId).getSingleResult();
List<Order> page = session.createSelectionQuery(
        "from Order o where o.customer.id = :id order by o.createdAt desc", Order.class)
    .setParameter("id", customerId).setPage(Page.first(20)).getResultList();
```

## Many-to-many with Set

```java
@ManyToMany
@JoinTable(name = "book_tag",
        joinColumns = @JoinColumn(name = "book_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"))
private Set<Tag> tags = new HashSet<>();
```

Keep it unidirectional unless `Tag` really needs to navigate to its books. If it does, add
`@ManyToMany(mappedBy = "tags") Set<Book> books` on `Tag` and update both sets in `addTag`/`removeTag`,
as in A2.

The moment the link needs an attribute (e.g. `taggedAt`), replace it with a `BookTag` entity.
