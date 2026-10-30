#!/usr/bin/env python3
"""Static checks for common Hibernate ORM 7 / Jakarta Persistence mistakes.

Usage:
    python3 check_entities.py [--strict] [PATH ...]

Scans .java files (and config files for a few settings) below the given paths
(default: current directory). Prints findings as
    path:line: SEVERITY [RULE] message
Exit code 1 if any ERROR was found (or any WARNING with --strict), else 0.

This is a heuristic, regex-based linter: it has no type information, so treat
WARNING/INFO findings as prompts to look closer, not as proof.
"""
import argparse
import os
import re
import sys

SKIP_DIRS = {".git", "target", "build", "out", "node_modules", ".idea", ".gradle"}
CONFIG_SUFFIXES = (".properties", ".yml", ".yaml", ".xml")

ERROR, WARNING, INFO = "ERROR", "WARNING", "INFO"
SEVERITY_ORDER = {ERROR: 0, WARNING: 1, INFO: 2}


def strip_comments(source, blank_strings=False):
    """Replace comments (and optionally string contents) with spaces, keeping offsets and newlines."""
    out = list(source)
    i, n = 0, len(source)

    def blank(start, end):
        if blank_strings:
            for j in range(start, min(end, n)):
                if out[j] != "\n":
                    out[j] = " "

    while i < n:
        c = source[i]
        if source.startswith('"""', i):
            end = source.find('"""', i + 3)
            end = n if end == -1 else end
            blank(i + 3, end)
            i = end + 3
        elif c == '"' or c == "'":
            start = i + 1
            i += 1
            while i < n and source[i] != c and source[i] != "\n":
                i += 2 if source[i] == "\\" else 1
            blank(start, i)
            i += 1
        elif source.startswith("//", i):
            while i < n and source[i] != "\n":
                out[i] = " "
                i += 1
        elif source.startswith("/*", i):
            end = source.find("*/", i + 2)
            end = n if end == -1 else end + 2
            for j in range(i, end):
                if out[j] != "\n":
                    out[j] = " "
            i = end
        else:
            i += 1
    return "".join(out)


def matching_paren(text, open_index, open_char="(", close_char=")"):
    depth = 0
    for i in range(open_index, len(text)):
        if text[i] == open_char:
            depth += 1
        elif text[i] == close_char:
            depth -= 1
            if depth == 0:
                return i
    return len(text) - 1


def annotations(text, names):
    """Yield (name, args, start, end) for annotations with the given simple names."""
    pattern = re.compile(r"@(?:[\w.]+\.)?(" + "|".join(names) + r")\b")
    for m in pattern.finditer(text):
        end = m.end()
        args = ""
        rest = text[end:]
        stripped = rest.lstrip()
        if stripped.startswith("("):
            open_index = end + (len(rest) - len(stripped))
            close = matching_paren(text, open_index)
            args = text[open_index + 1:close]
            end = close + 1
        yield m.group(1), args, m.start(), end


def declaration_after(text, index):
    """Text from index up to the end of the annotated declaration (';' or '{')."""
    m = re.compile(r"[;{]").search(text, index)
    return text[index:m.end()] if m else text[index:]


STRING_CHAIN = re.compile(r'"(?:[^"\\\n]|\\.)*"(?:\s*\+\s*"(?:[^"\\\n]|\\.)*")*')
TEXT_BLOCK = re.compile(r'"""(.*?)"""', re.S)
HQL_KEYWORDS = {"where", "join", "left", "inner", "right", "full", "cross", "fetch", "order", "group",
                "on", "with", "having", "union", "limit", "offset", "as"}


def query_strings(text):
    """Yield (index, content) for text blocks and chains of "..." + "..." string literals."""
    for m in TEXT_BLOCK.finditer(text):
        yield m.start(), m.group(1)
    without_blocks = TEXT_BLOCK.sub(lambda m: " " * len(m.group(0)), text)
    for m in STRING_CHAIN.finditer(without_blocks):
        parts = re.findall(r'"((?:[^"\\\n]|\\.)*)"', m.group(0))
        yield m.start(), "".join(parts)


def without_subqueries(hql):
    """Remove parenthesized subqueries, which may legitimately reuse an alias name."""
    result = hql
    while True:
        m = re.search(r"\(\s*select\b", result, re.I)
        if not m:
            return result
        close = matching_paren(result, m.start())
        result = result[:m.start()] + " " + result[close + 1:]


def filtered_fetch_alias(hql):
    """Return the alias of a join-fetched association that is referenced in the WHERE clause, if any."""
    outer = without_subqueries(hql)
    where = re.search(r"\bwhere\b(.*?)(\border\s+by\b|\bgroup\s+by\b|$)", outer, re.I | re.S)
    if not where:
        return None
    for m in re.finditer(r"\bjoin\s+fetch\s+[\w.]+\s+(?:as\s+)?(\w+)", outer, re.I):
        alias = m.group(1)
        if alias.lower() in HQL_KEYWORDS:
            continue
        if re.search(r"(?<![\w.])" + re.escape(alias) + r"\s*\.", where.group(1)):
            return alias
    return None


def line_of(text, index):
    return text.count("\n", 0, index) + 1


def check_java(path, raw):
    text = strip_comments(raw)  # string literals kept: query strings, setting names
    code = strip_comments(raw, blank_strings=True)
    findings = []

    def add(index, severity, rule, message):
        findings.append((path, line_of(text, index), severity, rule, message))

    is_entity = re.search(r"@(?:jakarta\.persistence\.)?Entity\b", code) is not None

    for m in re.finditer(r"^\s*import\s+(?:static\s+)?javax\.persistence\b", code, re.M):
        add(m.start(), ERROR, "HIB001",
            "javax.persistence is not supported by Hibernate 7; import jakarta.persistence.")

    for m in re.finditer(r"FetchType\.EAGER", code):
        add(m.start(), WARNING, "HIB002",
            "FetchType.EAGER cannot be overridden per query and causes N+1 selects; "
            "map LAZY and fetch per use case (join fetch / entity graph).")

    for name, args, start, end in annotations(code, ["ManyToOne", "OneToOne"]):
        if "LAZY" not in args and "EAGER" not in args:
            add(start, WARNING, "HIB003",
                "@%s is EAGER by default; add fetch = FetchType.LAZY." % name)
        if re.search(r"CascadeType\.(ALL|REMOVE)", args) and name == "ManyToOne":
            add(start, WARNING, "HIB007",
                "Cascading ALL/REMOVE from child to parent deletes shared parents; "
                "cascade only from parent (@OneToMany) to child.")

    for name, args, start, end in annotations(code, ["ManyToMany"]):
        if re.search(r"CascadeType\.(ALL|REMOVE)", args):
            add(start, WARNING, "HIB007",
                "Cascading ALL/REMOVE on @ManyToMany deletes entities still referenced elsewhere; "
                "use PERSIST/MERGE at most.")
        if re.search(r"\b(List|Collection)\s*<", declaration_after(code, end)):
            add(start, WARNING, "HIB011",
                "@ManyToMany mapped as List/Collection is a bag (deletes and re-inserts all rows on change, "
                "MultipleBagFetchException); use Set.")

    for name, args, start, end in annotations(code, ["Enumerated"]):
        if "STRING" not in args:
            add(start, WARNING, "HIB004",
                "@Enumerated defaults to ORDINAL; reordering constants corrupts data. Use EnumType.STRING.")

    for m in re.finditer(r"\b\w*[sS]ession\s*\.\s*(save|saveOrUpdate|createCriteria)\s*\(", code):
        removed_in, replacement = {"save": ("7", "persist()"),
                                   "saveOrUpdate": ("7", "persist() for new or merge() for detached entities"),
                                   "createCriteria": ("6", "HQL or the JPA CriteriaBuilder")}[m.group(1)]
        add(m.start(), ERROR, "HIB005",
            "Session.%s() was removed in Hibernate %s; use %s." % (m.group(1), removed_in, replacement))
    for m in re.finditer(r"\b\w*[sS]ession\s*\.\s*load\s*\(\s*[\w.]+\.class", code):
        add(m.start(), ERROR, "HIB005",
            "Session.load(Class, id) was removed in Hibernate 7; use getReference() for a proxy or find() to load.")

    query_call = re.compile(
        r"\.(create(?:Selection|Mutation|Native)?Query|createSQLQuery)\s*\(\s*"
        r"(?:\"(?:[^\"\\\n]|\\.)*\"\s*\+\s*)+[A-Za-z_(]")
    for m in query_call.finditer(text):
        add(m.start(), ERROR, "HIB006",
            "Query string built by concatenation (injection risk, no plan caching); "
            "use :named parameters and setParameter().")
    for m in re.finditer(r"\.(create(?:Selection|Mutation|Native)?Query)\s*\(\s*String\.format\s*\(", text):
        add(m.start(), ERROR, "HIB006",
            "Query string built with String.format; use :named parameters and setParameter().")

    for index, query in query_strings(text):
        alias = filtered_fetch_alias(query)
        if alias:
            add(index, WARNING, "HIB014",
                "Join-fetched alias '%s' is restricted in WHERE. If it is a collection, the managed entity gets a "
                "partial collection: filter with an exists subquery and fetch the collection unrestricted "
                "(harmless for to-one associations)." % alias)

    if is_entity:
        if re.search(r"@GeneratedValue\b", code):
            for m in re.finditer(r"\bint\s+hashCode\s*\(\s*\)\s*\{", code):
                close = matching_paren(code, m.end() - 1, "{", "}")
                body = code[m.end():close]
                if re.search(r"Objects\.hash(Code)?\s*\([^)]*\bid\b|\bid\s*\.\s*hashCode\s*\(|getId\(\)\s*\.\s*hashCode", body):
                    add(m.start(), WARNING, "HIB008",
                        "hashCode() uses a generated id, so it changes on persist (entity lost in HashSet). "
                        "Return a constant or use a natural id.")
        for m in re.finditer(r"@(?:lombok\.)?(Data|EqualsAndHashCode|ToString)\b", code):
            add(m.start(), WARNING, "HIB009",
                "Lombok @%s on an entity generates equals/hashCode/toString over all fields, "
                "including lazy associations; write them by hand." % m.group(1))
        for m in re.finditer(r"\bfinal\s+class\s+\w+", code):
            add(m.start(), WARNING, "HIB012",
                "Final entity classes cannot be proxied, so lazy to-one associations are fetched eagerly.")
        for m in re.finditer(r"GenerationType\.IDENTITY", code):
            add(m.start(), INFO, "HIB010",
                "IDENTITY disables JDBC insert batching; prefer GenerationType.SEQUENCE where the database supports it.")

    if "enable_lazy_load_no_trans" in text:
        m = re.search(r"enable_lazy_load_no_trans", text)
        add(m.start(), ERROR, "HIB013",
            "enable_lazy_load_no_trans opens a new session per lazy access (hidden N+1, inconsistent data); "
            "fetch what you need inside the transaction.")
    return findings


def check_config(path, text):
    findings = []
    for m in re.finditer(r"enable_lazy_load_no_trans\W*[=:>\s\"]*true", text):
        findings.append((path, line_of(text, m.start()), ERROR, "HIB013",
                         "enable_lazy_load_no_trans=true hides N+1 problems; fetch inside the transaction."))
    return findings


def iter_files(paths):
    for root_path in paths:
        if os.path.isfile(root_path):
            yield root_path
            continue
        for root, dirs, files in os.walk(root_path):
            dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS)
            for name in sorted(files):
                if name.endswith(".java") or name.endswith(CONFIG_SUFFIXES):
                    yield os.path.join(root, name)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("paths", nargs="*", default=["."])
    parser.add_argument("--strict", action="store_true", help="also fail on warnings")
    args = parser.parse_args(argv)

    findings = []
    for path in iter_files(args.paths):
        try:
            with open(path, encoding="utf-8", errors="replace") as f:
                text = f.read()
        except OSError as e:
            print("%s: could not read: %s" % (path, e), file=sys.stderr)
            continue
        findings.extend(check_java(path, text) if path.endswith(".java") else check_config(path, text))

    findings.sort(key=lambda f: (f[0], f[1], SEVERITY_ORDER[f[2]]))
    for path, line, severity, rule, message in findings:
        print("%s:%d: %s [%s] %s" % (path, line, severity, rule, message))

    errors = sum(1 for f in findings if f[2] == ERROR)
    warnings = sum(1 for f in findings if f[2] == WARNING)
    print("%d error(s), %d warning(s), %d info" % (errors, warnings, len(findings) - errors - warnings))
    return 1 if errors or (args.strict and warnings) else 0


if __name__ == "__main__":
    sys.exit(main())
