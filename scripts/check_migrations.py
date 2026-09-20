#!/usr/bin/env python3
"""Replay the hand-written Room migrations and check where they land.

A migration that produces the wrong schema is a crash on launch, for everybody
who had the previous version installed — the one failure in this app that
cannot be recovered from by the reader. Room validates the schema when it opens
the database and throws if it differs by so much as a column's nullability.

There are no instrumented tests here, so nothing was checking this before the
APK reached a phone. This does the next best thing on the JVM: it builds the
starting schema from the exported JSON, runs the migrations' own SQL — read out
of NeoFeedDb.kt rather than retyped, so it checks what actually ships — and
compares the result against the exported schema for the target version, which
is exactly what Room will compare against.

What it covers: columns, their types and nullability, indices, foreign keys,
`PRAGMA foreign_key_check`, and which rows survive. What it does not: Room's
identityHash, the auto-migrations (which have no SQL to read), and anything
about the app above the database. A pass here is not a promise the upgrade
works; a failure is a promise it does not.

    python3 scripts/check_migrations.py            # 22 -> latest exported
    python3 scripts/check_migrations.py --from 20
"""

import argparse
import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/saulhdev/feeder/data/db/NeoFeedDb.kt"
SCHEMAS = ROOT / "app/schemas/com.saulhdev.feeder.data.db.NeoFeedDb"


def migration_sql(source, name):
    """The execSQL statements in one migration object, in source order.

    Adjacent string literals are joined, because a statement too long for one
    line is built by concatenation and half of it is not valid SQL.
    """
    start = source.index(f"object {name} : Migration(")
    nxt = source.find('\n@Suppress("ClassName")\nobject ', start + 10)
    body = source[start: nxt if nxt > 0 else len(source)]

    out, i = [], 0
    while True:
        i = body.find("db.execSQL(", i)
        if i < 0:
            return out
        j, depth = i + len("db.execSQL("), 1
        while depth:
            if body[j] == "(":
                depth += 1
            elif body[j] == ")":
                depth -= 1
            j += 1
        arg = body[i + len("db.execSQL("): j - 1]
        parts = re.findall(r'"""(.*?)"""|"((?:[^"\\\n]|\\.)*)"', arg, re.S)
        out.append("".join((a or b) for a, b in parts).strip())
        i = j


def schema(version):
    path = SCHEMAS / f"{version}.json"
    if not path.is_file():
        sys.exit(f"no exported schema for version {version}")
    return json.load(open(path))["database"]


def build(version):
    """A database at `version`, created the way Room would create it."""
    con = sqlite3.connect(":memory:")
    d = schema(version)
    for e in d["entities"]:
        con.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for i in e.get("indices", []):
            con.execute(i["createSql"].replace("${TABLE_NAME}", e["tableName"]))
    return con


def seed(con):
    """One ordinary feed and one Mastodon feed, each with an article.

    Rows rather than an empty database, because the interesting failures are
    about what a migration does to data: a rebuild that loses it, a delete that
    takes too much, a foreign key left pointing at a table that was renamed.
    """
    cols = {r[1] for r in con.execute("PRAGMA table_info(Feeds)")}

    def feed(title, url, source_type):
        named = {"title": title, "url": url, "description": "", "feedImage": "",
                 "tag": "", "sourceType": source_type, "isEnabled": 1,
                 "fullTextByDefault": 1}
        keys = [k for k in named if k in cols]
        rest = [(n, t, nn, df, pk) for _, n, t, nn, df, pk
                in con.execute("PRAGMA table_info(Feeds)")
                if n not in named and nn and df is None and not pk]
        names = keys + [r[0] for r in rest]
        vals = [named[k] for k in keys] + [0 for _ in rest]
        con.execute(
            f"INSERT INTO Feeds ({','.join(names)}) VALUES ({','.join('?' * len(names))})",
            vals,
        )

    feed("BBC", "https://bbc.co.uk/rss", "rss")
    feed("Toots", "http://mastodon://m.social/@a", "mastodon")

    info = [(r[1], r[2], r[3], r[4], r[5]) for r in con.execute("PRAGMA table_info(Article)")]

    def article(feed_id, guid):
        named = {"feedId": feed_id, "guid": guid, "uuid": guid}
        names, vals = [], []
        for name, typ, notnull, default, pk in info:
            if name in named:
                names.append(name); vals.append(named[name])
            elif notnull and default is None and not pk:
                names.append(name)
                vals.append(0 if typ.upper() in ("INTEGER", "REAL") else "")
        con.execute(
            f"INSERT INTO Article ({','.join(names)}) VALUES ({','.join('?' * len(names))})",
            vals,
        )

    article(1, "keep-me")
    article(2, "toot-1")
    con.commit()


def compare(con, target):
    """Whether the migrated database is what Room expects at `target`."""
    d = schema(target)
    ok = True
    for e in d["entities"]:
        table = e["tableName"]
        observed = {
            r[1]: (r[2], bool(r[3]))
            for r in con.execute(f"PRAGMA table_info(`{table}`)")
        }
        if not observed:
            print(f"  FAIL {table}: table does not exist")
            ok = False
            continue

        expected = {
            f["columnName"]: (f.get("affinity", ""), bool(f.get("notNull", False)))
            for f in e["fields"]
        }
        if set(observed) != set(expected):
            ok = False
            print(f"  FAIL {table}: columns differ")
            print(f"    only in database: {sorted(set(observed) - set(expected))}")
            print(f"    only in schema:   {sorted(set(expected) - set(observed))}")
        else:
            for col, (affinity, not_null) in expected.items():
                seen_affinity, seen_not_null = observed[col]
                if affinity and seen_affinity.upper() != affinity.upper():
                    ok = False
                    print(f"  FAIL {table}.{col}: type {seen_affinity}, schema says {affinity}")
                if seen_not_null != not_null:
                    ok = False
                    print(f"  FAIL {table}.{col}: notNull {seen_not_null}, schema says {not_null}")

        indices = {
            r[0] for r in con.execute(
                "SELECT name FROM sqlite_master "
                "WHERE type='index' AND tbl_name=? AND sql IS NOT NULL",
                (table,),
            )
        }
        missing = {i["name"] for i in e.get("indices", [])} - indices
        if missing:
            ok = False
            print(f"  FAIL {table}: missing indices {sorted(missing)}")

    violations = list(con.execute("PRAGMA foreign_key_check"))
    if violations:
        ok = False
        print(f"  FAIL foreign_key_check: {violations}")

    return ok


def main():
    latest = max(int(p.stem) for p in SCHEMAS.glob("*.json"))
    parser = argparse.ArgumentParser()
    parser.add_argument("--from", dest="start", type=int, default=22)
    parser.add_argument("--to", dest="end", type=int, default=latest)
    args = parser.parse_args()

    source = SRC.read_text()
    con = build(args.start)
    seed(con)

    for version in range(args.start, args.end):
        name = f"MIGRATION_{version}_{version + 1}"
        if f"object {name} : Migration(" not in source:
            sys.exit(f"{name} is not in {SRC.name} — an auto-migration cannot be replayed")
        statements = migration_sql(source, name)
        print(f"{name}: {len(statements)} statements")
        for statement in statements:
            try:
                con.execute(statement)
            except sqlite3.Error as error:
                sys.exit(f"  FAIL {name}: {error}\n{statement}")
    con.commit()

    ok = compare(con, args.end)

    print("\nSurviving rows:")
    for row in con.execute("SELECT id, title FROM Feeds ORDER BY id"):
        print(f"  feed    {row}")
    for row in con.execute("SELECT feedId, guid FROM Article ORDER BY guid"):
        print(f"  article {row}")

    print()
    if ok:
        print(f"PASS: {args.start} -> {args.end} lands on the schema Room expects")
    else:
        print(f"FAIL: {args.start} -> {args.end} would be rejected on launch")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
