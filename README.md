# Hibernate ORM AI Guidelines

[![CI](https://github.com/niklas-enns/Hibernate-ORM-AI-Guidelines/actions/workflows/ci.yml/badge.svg)](https://github.com/niklas-enns/Hibernate-ORM-AI-Guidelines/actions/workflows/ci.yml)

Guidelines that teach AI coding agents how to write correct, efficient **Hibernate ORM 7 / Jakarta Persistence 3.2**
code. Each rule tagged ✅ is backed by a test that runs against a real Hibernate version, so the guidelines can't
quietly drift into outdated Hibernate 5 folklore.

This is an independent community project. It is not affiliated with, sponsored or endorsed by the Hibernate
project. Hibernate is a trademark of its respective owner.

**Quick start (Claude Code):**
```
/plugin install hibernate-orm --marketplace niklas-enns/Hibernate-ORM-AI-Guidelines
```
On Claude Code before 2.1.275, run `/plugin marketplace add niklas-enns/Hibernate-ORM-AI-Guidelines`, then
`/plugin install hibernate-orm@hibernate-orm-ai-guidelines`. Other tools: see [Using it](#using-it).

## What's in here

```
skills/hibernate-orm/              Agent Skill (Claude Code, Claude API, and other tools that support Agent Skills)
├── SKILL.md                       core rules + workflow; loaded when the agent works on Hibernate code
├── references/                    detailed topics, read on demand
│   ├── entity-mapping.md
│   ├── associations.md
│   ├── fetching.md
│   ├── querying.md
│   ├── sessions-and-transactions.md
│   ├── testing.md
│   └── anti-patterns.md           review checklist
└── scripts/check_entities.py      static checker for common mistakes (Python 3.9+, no dependencies)

.claude-plugin/                    Claude Code plugin + marketplace manifests

agents-md/AGENTS.md                condensed rules for tools that read AGENTS.md (generated from SKILL.md)

src/main/java/.../model/           reference model that follows every rule (Author, Book, Publisher, ...)
src/test/java/.../                 tests proving each rule; .../antipattern/ holds deliberately wrong mappings
tools/build_agents_md.py           regenerates agents-md/AGENTS.md
```

## Using it

**As a Claude Code plugin.** See the quick start above. The skill then runs as `/hibernate-orm:hibernate-orm`
and also loads automatically.

**As an Agent Skill** (Claude Code without the plugin, or other tools). Copy `skills/hibernate-orm` to either
- `~/.claude/skills/hibernate-orm` (all your projects), or
- `<project>/.claude/skills/hibernate-orm` (one project, shared with the team via git).

The agent loads it automatically when it works on entities, queries or sessions.

**As AGENTS.md.** Copy `agents-md/AGENTS.md` into your project root, or append it to an existing `AGENTS.md` /
`CLAUDE.md`.

**Checker** (works with or without an agent):
```bash
python3 skills/hibernate-orm/scripts/check_entities.py src/main/java          # exit 1 on errors
python3 skills/hibernate-orm/scripts/check_entities.py --strict src/main/java # also fail on warnings (CI)
```

**Checker in your CI.** It catches mistakes no matter who or which model wrote the code, at no token cost.
It needs only Python 3.9+, no dependencies. GitHub Actions example, with the skill committed under
`.claude/skills/hibernate-orm`:
```yaml
- name: Hibernate mapping checks
  run: python3 .claude/skills/hibernate-orm/scripts/check_entities.py --strict src/main/java
```
Start without `--strict` (fail on errors only) if existing code has many warnings, and tighten later.

## Verifying the guidelines

```bash
mvn test                                         # Java 25, Hibernate 7.4.14.Final, H2
tools/test-hibernate-versions.sh                 # same suite on 7.0, 7.1, 7.2, 7.3, 7.4.0, 7.4.14
python3 tools/build_agents_md.py --check         # AGENTS.md in sync with SKILL.md
```

The full suite passes on every listed version. The only version-dependent rule is F4 (see below); its test
checks the behavior expected for the version on the classpath.

| Rule | Verified by |
|---|---|
| M3 equals/hashCode | `EntityMappingTest`: `idBasedHashCodeBreaksHashSet`, `constantHashCodeSurvivesPersist`, `equalsWorksWithProxies`, `naturalIdEquality` |
| M4 enums as STRING | `EntityMappingTest`: `ordinalEnumStoresPosition`, `stringEnumStoresName` |
| M5 records as embeddables | `EntityMappingTest#recordAsEmbeddable` |
| M6 non-final entities | `ClassShapeTest`: `finalEntityCannotBeLazy`, `nonFinalEntityStaysLazy` |
| M7 toString / Lombok | `ClassShapeTest`: `toStringWithLazyCollection`, `toStringWithoutAssociations` |
| A1 LAZY to-one | `FetchingTest`: `eagerToOneCausesNPlusOne`, `lazyToOneLoadsNothingExtra` |
| A2 owning side / helpers | `AssociationTest`: `inverseSideOnlyIsIgnored`, `helperMethodSetsOwningSide` |
| A3 cascades | `AssociationTest#cascadeFromChildToParentDeletesSharedParent` |
| A4 multiple bags | `AssociationTest`: `twoBagsCannotBeFetchedTogether`, `fetchCollectionsInSeparateQueries` |
| A5 orphanRemoval | `AssociationTest#orphanRemovalDeletesChild` |
| A6 bounded collections | `AssociationTest#mappedCollectionLoadsEverything` |
| F1 N+1 / join fetch | `FetchingTest`: `lazyCollectionInLoopCausesNPlusOne`, `joinFetchLoadsInOneSelect` |
| F2 entity graphs | `FetchingTest#entityGraphFetchesCollection` |
| F3 batch fetching | `BatchFetchingTest#batchFetchingLimitsNPlusOne` |
| F4 pagination | `FetchingTest`: `paginationWithCollectionFetch`, `paginateIdsThenFetch` |
| F5 DTO projections | `FetchingTest#recordProjection` |
| F6 LazyInitializationException | `FetchingTest#lazyAccessOutsideSessionFails` |
| Q1 bound parameters | `QueryingTest#parametersAreNotInterpreted` |
| Q3 no DISTINCT | `QueryingTest#joinFetchDeduplicatesRoots` |
| Q4 bulk operations | `QueryingTest#bulkUpdateBypassesPersistenceContext` |
| Q5 filtered fetch | `QueryingTest`: `filteringFetchedCollectionCorruptsState`, `filterWithSeparateJoin` |
| S1 dirty checking | `SessionLifecycleTest#dirtyCheckingWritesChanges` |
| S2 merge | `SessionLifecycleTest#mergeReturnsManagedCopy` |
| S5 batch processing | `SessionLifecycleTest`: `regularSessionKeepsEveryEntity`, `flushAndClearBoundsMemory`, `statelessSessionForBulkInsert`, `statelessSessionBatchSize` |
| C1 optimistic locking | `SessionLifecycleTest#optimisticLockingRejectsStaleMerge` |

Not covered by tests: M1, M2, Q2, S3, S4 (taken from the Hibernate documentation and API; the removed
APIs in S3 were checked against the Hibernate 7.4.14 `Session` interface).

Limitations: tests run on H2 only. Behavior that depends on the database (SQL shape, locking, sequences) can differ.

## Does the skill help agents?

`skills/hibernate-orm/evals/` holds 5 coding tasks (skill-creator format). Each run gets a copy of
`eval-harness/starter`. `eval-harness/grade.py` grades the result with hidden tests (`eval-harness/hidden-tests`):
correctness, SQL statement counts, mapping checks.

Results of the first evaluation (1 run per configuration, so indicative only):

| | Opus with skill | Opus without | Haiku with skill | Haiku without |
|---|---|---|---|---|
| Checks passed | 25/25 | 24/25 | 25/25 | 23/25 |
| Tokens per task | 52k | 39k | 56k | 46k |

Without the skill, both models already handled N+1, aggregates, bulk import and partial updates correctly. The
skill made the difference on the house rules (`@Version`, `equals`/`hashCode`) and prevented Haiku from leaving
three `@ManyToOne` associations EAGER. `SKILL.md` was then condensed to an entity checklist, house rules and
Hibernate 7 specifics (v2), and measured again against the same baselines:

| | Opus v1 | Opus v2 | Haiku v1 | Haiku v2 |
|---|---|---|---|---|
| Checks passed | 25/25 | 25/25 | 25/25 | 25/25 |
| Token overhead vs. no skill | +13.4k | **+6.5k** | +10.4k | **+9.2k** |
| Files with `@Version` / `equals`+`hashCode` | 5 / 5 | 6 / 6 | 1 / 1 | **4 / 5** |

The checklist at the top of v2 is what made Haiku apply the house rules.

### A finding from writing the tests

The widespread rule "never combine pagination with a collection `join fetch`" is **outdated since Hibernate 7.4.0**.
Hibernate now pages the root entities in a derived table and returns complete collections in a single statement.
Up to 7.3.x it still loads all rows and pages in memory; the cross-version run pins the change to 7.4.0. The
guidelines keep `fail_on_pagination_over_collection_fetch=true` as a safeguard (F4).

## License

MIT, see [LICENSE](LICENSE).
