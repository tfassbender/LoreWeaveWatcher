# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

The repository currently contains only `doc/LOREWEAVE_WATCH_PLAN.md` — the full implementation plan. No Gradle build, source, or tests exist yet. When scaffolding, follow the plan's phase order; don't skip ahead (e.g., don't add the HTTP server before the parser is vendored).

## What this project is

`lore-weave-watch` is a **single fat jar** that runs locally inside an Obsidian vault's `.loreweave/` directory, serves a tiny HTTP UI that polls for validation issues once per second, and auto-detects the vault root from the jar's own install location. It is **not** a server product — no auth, no git, no persistence. The browser is the UI; there is no GUI framework.

Sibling repo: [LoreWeave](https://github.com/tfassbender/LoreWeave) owns the canonical parser, schema, and validation rules. This repo **vendors** those packages (`domain/`, `parsing/`, `graph/{Index, IndexedNote, IndexBuilder, LinkResolver, ResolvedLink, ValidationReport, VaultScanner}`) and must stay byte-identical in behavior. Record the source commit SHA in `COPYING_NOTES.md` whenever the vendored copy is refreshed.

## Architecture contract

- **Two modes, one jar, one code path**: `watch` (default — HTTP server + browser UI, rebuilds index on every ~1 s poll) and `check` (headless CLI, one-shot validation with exit codes `0`/`1`/`2`/`3` for clean/warnings/errors/scan-failure). Both call the same `IndexBuilder.build(vault)`. Do not fork validation logic between them.
- **Zero config**: vault root is derived as the jar's parent-of-parent (`<vault>/.loreweave/lore-weave-watch.jar` → `<vault>`). `--vault <path>` is the escape hatch. If the detected root has no `.md` files, walk upward.
- **Minimal deps**: JDK `com.sun.net.httpserver` + commonmark-java + snakeyaml-engine + JUnit 5 + AssertJ. **Do not** add Jackson, Quarkus, Spring, Picocli, or any web framework — the plan calls for hand-rolled JSON and a tiny hand-rolled arg parser. Issue ordering (severity desc, category asc, path asc, message asc) is load-bearing for UI stability across polls.
- **Package layout** (target): `com.tfassbender.loreweave.watch.{cli, server, ui, parser, graph, domain}`.
- **UI**: single classpath `index.html` + vanilla JS. Rendering must be **diff-aware per issue** so scroll position survives polls; server-down must not wipe the last rendered list.

## Porting rules (vendored code)

- Parser bug fixes originate in LoreWeave, then get ported here. Never fix parser bugs independently in this repo.
- When porting, strip anything git- or Quarkus-related (`SyncService`, `GitVaultClient`, JAX-RS resources, DTOs) — those stay in the main repo.
- Port matching unit tests alongside the code so behavioral parity is provable.

## Build (once scaffolded)

Planned: Gradle Kotlin DSL, Java 21 toolchain, `com.gradleup.shadow` for the uber-jar. Expected commands (not yet live):
- `./gradlew shadowJar` → `build/libs/lore-weave-watch.jar`
- `./gradlew test` → JUnit 5 suite
- `java -jar build/libs/lore-weave-watch.jar` → watch mode (default port 4718, falls back to OS-picked)
- `java -jar build/libs/lore-weave-watch.jar check [--json] [--severity=errors|warnings|all] <vault>` → headless mode
