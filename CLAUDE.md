# Working on this repository

This repo produces Hibernate ORM 7 guidelines for AI agents (see README.md). The deliverables are
`skills/hibernate-orm/` and the generated `agents-md/AGENTS.md`. The Java code exists to prove the rules.

## Rules for changes

- A rule marked ✅ must have a test in `src/test/java` whose `@DisplayName` starts with the rule id (e.g. `F4: ...`),
  and must be listed in the README's rule table. Never mark a rule ✅ without a passing test.
- When a test contradicts a rule, the test (actual Hibernate behavior) wins: fix the rule text.
- Anti-pattern entities live only in `src/test/java/.../antipattern`. `src/main` must pass
  `python3 skills/hibernate-orm/scripts/check_entities.py --strict src/main`.
- `agents-md/AGENTS.md` is generated: edit the "Core rules" section of `SKILL.md`, then run
  `python3 tools/build_agents_md.py`.
- Keep `SKILL.md` short (core rules + workflow). Details and examples belong in `references/`.
- When upgrading Hibernate in `pom.xml`, add the new version to `tools/test-hibernate-versions.sh`, run it, and
  update version-specific statements (search for "7.4"). A rule that differs between versions must say from which
  version on it applies, and its test must branch with `hibernateAtLeast(major, minor)`.

## Commands

```bash
mvn test
tools/test-hibernate-versions.sh
python3 skills/hibernate-orm/scripts/check_entities.py --strict src/main
python3 tools/build_agents_md.py --check
```

## Releases

The repo is also a Claude Code plugin marketplace (`.claude-plugin/`). Plugin users only receive changes when
`version` in `.claude-plugin/plugin.json` changes: bump it with every release and tag the commit `v<version>`.
Check with `claude plugin validate .` (the CLAUDE.md warning is expected: this file is for contributors).
