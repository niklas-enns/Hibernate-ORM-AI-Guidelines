#!/usr/bin/env python3
"""Generate agents-md/AGENTS.md from the core rules in skills/hibernate-orm/SKILL.md.

Usage:
    python3 tools/build_agents_md.py          # write the file
    python3 tools/build_agents_md.py --check  # exit 1 if the file is out of date
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKILL = os.path.join(ROOT, "skills", "hibernate-orm", "SKILL.md")
TARGET = os.path.join(ROOT, "agents-md", "AGENTS.md")

HEADER = """\
<!-- GENERATED from skills/hibernate-orm/SKILL.md by tools/build_agents_md.py. Do not edit by hand. -->
# Hibernate ORM 7 rules for AI agents

Applies to all code using Hibernate ORM 7 / Jakarta Persistence 3.2 in this project.
Many Hibernate examples online target Hibernate 5 or older. When they contradict these rules, follow these rules.
`Session.save()`, `update()`, `saveOrUpdate()`, `delete()`, `load(Class, id)` and the legacy `Criteria` API
no longer exist.

Rules marked ✅ are verified by tests on Hibernate 7.0 through 7.4.
Full guidelines with examples: the `hibernate-orm` Agent Skill.

"""


def build():
    with open(SKILL, encoding="utf-8") as f:
        skill = f.read()
    match = re.search(r"^## Core rules\n(.*)", skill, re.M | re.S)
    if not match:
        sys.exit("SKILL.md has no '## Core rules' section")
    return HEADER + "## Rules\n" + match.group(1).rstrip() + "\n"


def main():
    content = build()
    if "--check" in sys.argv:
        with open(TARGET, encoding="utf-8") as f:
            if f.read() != content:
                print("agents-md/AGENTS.md is out of date; run tools/build_agents_md.py")
                return 1
        return 0
    with open(TARGET, "w", encoding="utf-8") as f:
        f.write(content)
    print("wrote " + os.path.relpath(TARGET, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
