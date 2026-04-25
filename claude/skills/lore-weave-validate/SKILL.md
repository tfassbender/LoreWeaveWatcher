---
name: lore-weave-validate
description: |
  Validates a LoreWeave Obsidian vault by shelling out to lore-weave-watch in
  headless `check` mode. Reports parse errors, missing required fields,
  unresolved [[wiki-links]], and missing-title/summary/schema_version warnings
  for every Markdown note in the vault. The output mirrors what the
  lore-weave-watch dashboard would show, just delivered as a single JSON blob.

  Trigger when the user says any of:
    - "validate my LoreWeave vault"
    - "check the vault for issues"
    - "lore-weave validate"
    - "are there any unresolved links?"
    - "what's broken in my vault?"
---

# lore-weave-validate

This skill runs the watcher's one-shot `check` mode against the user's vault
and surfaces the structured result.

## How it works

Shell out to:

```
java -jar "$LWW_JAR" check --json --vault "$VAULT_PATH"
```

- `$LWW_JAR` defaults to `~/tools/lore-weave-watch/lore-weave-watch.jar`. If
  the user has put the jar somewhere else (for example
  `<vault>/.loreweave/lore-weave-watch.jar`), prefer that path. Honour an
  `LWW_JAR` environment variable if it's set.
- `$VAULT_PATH` is the user's vault root. If you don't know it, ask once. If
  the jar is sitting inside a `<vault>/.loreweave/` directory, the watcher
  auto-detects the vault from the jar location, so passing `--vault` is
  optional in that case.

## Interpreting the output

The command prints a single JSON document to stdout:

```json
{
  "vault": "/abs/path/to/vault",
  "summary": { "errors": 2, "warnings": 1, "notes_served": 31, "notes_excluded": 1 },
  "issues": [
    { "category": "unresolved_links", "severity": "error",
      "path": "factions/independents.md",
      "message": "unresolved [[the-void]] in 'factions/independents'" }
  ],
  "scanned_at": "2026-04-25T14:35:00Z"
}
```

Exit codes:

| Code | Meaning |
|---|---|
| 0 | clean — no issues |
| 1 | warnings only |
| 2 | one or more errors |
| 3 | scan failed (vault not found, jar can't read it, etc.) |

When responding to the user:

1. Lead with the summary line: counts of errors, warnings, served, excluded.
2. If there are issues, group them by category and list the file path + the
   first line of each message.
3. If exit code is 3, surface stderr verbatim and suggest `--vault <path>` if
   auto-detection failed.

Keep the response terse — large vaults can produce dozens of issues.

## Don't

- Re-implement validation logic in this skill. The watcher's parser is the
  source of truth; everything else drifts.
- Modify the user's notes from this skill — `check` is read-only and the skill
  must stay that way.
