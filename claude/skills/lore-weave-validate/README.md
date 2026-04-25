# lore-weave-validate (Claude Code skill)

Wraps [lore-weave-watch](https://github.com/tfassbender/LoreWeaveWatcher)'s
headless `check` mode so you can ask Claude Code to validate a LoreWeave
vault from chat.

## Install

1. Build (or download a release of) `lore-weave-watch.jar`. From a clone of
   the watcher:

   ```bash
   ./gradlew shadowJar
   ```

   The jar lands at `build/libs/lore-weave-watch.jar`.

2. Drop the jar somewhere stable on your machine — for example
   `~/tools/lore-weave-watch/lore-weave-watch.jar`. The skill assumes that
   default path; override it by setting `LWW_JAR` in your environment if you
   keep the jar elsewhere.

3. Copy this directory into your Claude Code skills folder:

   ```bash
   cp -R claude/skills/lore-weave-validate ~/.claude/skills/
   ```

4. Restart any open Claude Code sessions so the new skill is picked up.

## Usage

In any Claude Code session, ask:

- "Validate my LoreWeave vault."
- "Check the vault at `~/notes/loreweave-vault` for issues."
- "Are there any unresolved links right now?"
- "Run lore-weave validate."

Claude will shell out to:

```
java -jar "$LWW_JAR" check --json --vault "$VAULT_PATH"
```

…and summarize the JSON result. Exit codes follow the watcher's convention:
`0` clean, `1` warnings only, `2` errors, `3` scan failed.

## Scope

This skill is intentionally thin — it does not re-implement any validation
logic; the parser inside the jar is the single source of truth. If the jar
output format changes, only the description in `SKILL.md` needs updating.
The skill never modifies vault files; `check` is read-only.
