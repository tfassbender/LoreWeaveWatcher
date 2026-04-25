# LoreWeave Watch – Implementation Plan

A live validation dashboard for LoreWeave Obsidian vaults. Drop the fat jar into a dot-prefixed folder inside your vault, launch it, and a browser tab shows what needs fixing. It updates on a short poll interval, so issues you introduce while editing in Obsidian appear within a second or two — no commit, no push, no server round-trip.

> This file is a starter plan meant to be copied into a new repository. Keep it as the top-level `doc/implementation_plan.md` there.

## Related repositories

- [LoreWeave](https://github.com/tfassbender/LoreWeave) — the REST API / server half of the system. This project is a sibling: it shares the parser, schema, and validation rules, and adds only the local dashboard UI on top.

## What this tool is (and isn't)

- It's a **tiny standalone binary** that runs on an author's machine.
- It's **not** a server, has **no** auth, has **no** git sync.
- It reads the filesystem directly — whatever's on disk is what gets validated. No commit/push required to see problems.
- It stays out of Obsidian's way: lives under a dot-prefixed directory the Obsidian client ignores (`.loreweave/` by convention, next to `.obsidian/`).

## Design principles

- **Zero config**: drop the jar in `<vault>/.loreweave/`, run it, and the tool infers the vault root (parent of the jar's own directory).
- **One artifact**: a single fat jar, `lore-weave-watch.jar`, built via `shadowJar`. Optional `.bat` / `.sh` launchers sit beside it.
- **Minimal deps**: `com.sun.net.httpserver` (JDK) + commonmark-java + snakeyaml-engine. No Quarkus, no Spring, no web framework. Hand-rolled JSON is fine at this scale.
- **Cheap refresh**: the page polls an HTTP endpoint every ~1 s; the server rebuilds the in-memory index on each poll. Parsing 500 notes is single-digit milliseconds — `WatchService` is unnecessary until vaults get much larger.
- **Obsidian-first**: `.loreweave/` sits next to `.obsidian/` and is naturally hidden from the vault. No Obsidian plugin required — it's just a local URL the author keeps open.

## Tech stack

- **Java 21** (records, sealed, pattern matching).
- **Gradle** (Kotlin DSL) with the `com.gradleup.shadow` (or equivalent) plugin for the uber-jar.
- **commonmark-java** + **snakeyaml-engine** — same versions as the LoreWeave main repo, so parsing behavior stays identical.
- **JUnit 5** + **AssertJ** for tests.
- **MIT** license.
- No GUI framework; the browser is the UI.

## Code-reuse strategy

The parser, schema types, and validation rules must be byte-identical to what the main LoreWeave server uses, or diagnostics here will drift from what the server would report.

Two realistic options:

1. **Vendored copy** (initial) — copy the relevant packages from the LoreWeave repo (`domain/*`, `parsing/*`, `graph/{Index, IndexedNote, IndexBuilder, LinkResolver, ResolvedLink, ValidationReport, VaultScanner}`). Track the source commit in a `COPYING_NOTES.md` at the project root. Pros: no cross-repo release ceremony; Cons: drift risk, manual sync.
2. **Shared library** (later) — promote those packages to a published `loreweave-core` Maven artifact, consumed by both repos. Pros: no drift; Cons: release process for every behavior change.

Start with (1). Promote to (2) when the drift becomes painful.

---

## Phase 1 — Project scaffold

**Exit criteria**: `./gradlew shadowJar` produces `build/libs/lore-weave-watch.jar`, `java -jar` runs and prints a help banner.

- [x] Gradle Kotlin DSL project, Java 21 toolchain, group `com.tfassbender.loreweave.watch`, MIT `LICENSE`.
- [x] `com.gradleup.shadow` plugin for fat-jar output.
- [x] Dependencies: commonmark-java, snakeyaml-engine, JUnit 5, AssertJ.
- [x] Package skeleton: `com.tfassbender.loreweave.watch.{cli, server, ui, parser, graph, domain}`. _(only `cli` materialized so far; remaining packages will be created as phase 2+ adds files.)_
- [x] `Main` class with a `--help` banner and a `--version` flag. Uses a tiny hand-rolled arg parser (Picocli is fine, but avoidable for this scope).

## Phase 2 — Vendor parser + graph from LoreWeave

**Exit criteria**: given a single markdown file string, this tool produces exactly the same `Note` + validation issues as the main LoreWeave repo.

- [x] Copy `domain/`, `parsing/`, and the graph-build classes from LoreWeave. Track the source commit SHA in `COPYING_NOTES.md`. _(parsing/ renamed to parser/ per target layout; source commit `1c85eb1f`.)_
- [x] Re-run the matching unit tests here to confirm behavioral parity (port them alongside the code). _(60 vendored tests + 10 cli tests = 71 passing; fixture vaults copied to `src/test/resources/vault-{valid,invalid}/`.)_
- [x] Strip anything git- or Quarkus-related (the `SyncService`, `GitVaultClient`, JAX-RS resources, DTOs, etc. all stay in the main repo). _(parser+graph were already framework-free in the source; sync/search/related classes simply not copied — see `COPYING_NOTES.md`.)_

## Phase 3 — Vault auto-detection

**Exit criteria**: launched from `<vault>/.loreweave/lore-weave-watch.jar`, the tool picks `<vault>` as the vault root without any args.

- [ ] Locate the jar at runtime via `WatchMain.class.getProtectionDomain().getCodeSource().getLocation()`.
- [ ] Default rule: vault root = jar's parent-of-parent (i.e., the dir that contains the `.loreweave/` folder).
- [ ] Sanity check: the detected root must contain at least one `.md` file. If not, walk upward looking for one.
- [ ] Override: `--vault <path>` flag for non-standard setups.
- [ ] Log the detected root on startup so operators can spot misdetection.

## Phase 4 — HTTP server + JSON API

**Exit criteria**: `GET /api/validation` returns the full issue list + summary, with a fresh scan on each request.

- [ ] Use `com.sun.net.httpserver.HttpServer`. Default port 4718; fall back to OS-picked if bound.
- [ ] Endpoints:
  - `GET /` — embedded `index.html`.
  - `GET /api/validation` — runs `IndexBuilder.build(<vault>)` and returns:
    ```json
    {
      "summary": { "errors": 2, "warnings": 1, "notes_served": 31, "notes_excluded": 1 },
      "issues": [
        { "category": "unresolved_links", "severity": "error",
          "path": "factions/independents.md", "message": "unresolved [[the-void]]" }
      ],
      "scanned_at": "2026-04-21T14:35:00Z"
    }
    ```
- [ ] Issues sorted by (severity desc, category asc, path asc, message asc) so the UI doesn't twitch between polls.
- [ ] Hand-rolled JSON writer is fine (no Jackson dep); all field types are strings/ints/arrays.
- [ ] Graceful shutdown on Ctrl-C: stop the HTTP server, exit 0.
- [ ] Auto-launch the browser on startup via `java.awt.Desktop#browse(URI)` once the server is listening (fall back to printing the URL if `Desktop` is unsupported or headless). Same behavior as phase 6 — implementing it here keeps the watch-mode UX usable from day one.
- [ ] Idle auto-shutdown: track the timestamp of the most recent `/api/validation` request. A background scheduler checks every second and exits cleanly if no poll has arrived within an idle threshold (default 10 s). Apply a startup grace window (~30 s) before the timeout becomes active so the browser has time to load. Any poll from any tab resets the timer, so multiple open tabs are handled implicitly. Threshold + grace window should be constants for now; expose as flags only if needed later. Known tradeoff: aggressive browser background-tab throttling or laptop sleep can trigger shutdown — keep the threshold generous and the launcher cheap to re-run rather than fighting the browser.

## Phase 5 — Browser UI

**Exit criteria**: page auto-polls `/api/validation`, renders grouped issues, updates without flicker as files change.

- [ ] Single `index.html` with vanilla JS, embedded as a classpath resource.
- [ ] Layout:
  - Top bar: vault path, last-scan timestamp (relative: "3 s ago"), `errors: N / warnings: N / notes served: N`.
  - Collapsible sections per category (errors first, then warnings). Each category expanded by default if it has issues.
  - Each issue row: severity dot + path + message. Empty state: "No issues — looking good!".
- [ ] Poll every ~1 s. Configurable via `?interval=<ms>` query param.
- [ ] Diff-aware rendering: update DOM per-issue rather than full re-render, so scroll position is preserved.
- [ ] Offline / server-down state: banner at the top, stop polling, don't blow away the last rendered list.

## Phase 6 — Packaging + launchers

**Exit criteria**: drop-in install: unzip a release archive into `<vault>/.loreweave/`, double-click (or `./lore-weave-watch.sh`), see results.

- [ ] `shadowJar` emits `lore-weave-watch.jar`.
- [ ] Launcher scripts shipped alongside:
  - `lore-weave-watch.bat` (Windows).
  - `lore-weave-watch.sh` (Unix, chmod +x).
- [ ] Browser auto-launch is already wired in phase 4; this phase just confirms it works from the launcher scripts on Windows + Linux.
- [ ] README covers: place the jar under `<vault>/.loreweave/`, run the launcher, note that `.loreweave/` is ignored by Obsidian by default (or add it to the user's Obsidian "Excluded files" if they've changed defaults).

## Phase 7 — Tests

**Exit criteria**: CI-green JUnit suite covering parser parity, validation, and an end-to-end server round-trip.

- [ ] Unit tests for every ported parser/graph class — mirror the main repo's tests.
- [ ] Server integration test: `HttpServer` against a fixture vault in `src/test/resources/`, hit `/api/validation`, assert the JSON shape and content.
- [ ] Launcher manual smoke test documented in the README (cross-platform: confirmed on Windows + Linux).

## Phase 8 — One-shot CLI mode + Claude Code skill

Moved here from the main LoreWeave repo (was its original phase 10). The skill wraps a headless `check` mode of this same jar, so the server UI and the scripted validator share every line of parser and graph code.

**Exit criteria**: `java -jar lore-weave-watch.jar check <vault-path>` prints a validation report and exits with a code that distinguishes clean / warnings-only / errors. A Claude Code skill copied into `~/.claude/skills/` invokes that command from chat and returns the structured output.

### 8.1 Headless `check` subcommand

- [ ] Add a `check` subcommand to the same `Main` dispatcher (so we have `lore-weave-watch.jar` for the default watch server and `lore-weave-watch.jar check …` for the one-shot mode — one artifact, two modes).
- [ ] Arguments: positional `<vault-path>` (defaults to the same auto-detect used in phase 3 if omitted); `--json` for structured output; `--severity=errors|warnings|all` (default `all`).
- [ ] Runs `IndexBuilder.build(path)` once, formats the `ValidationReport`, writes to stdout, exits. No HTTP server, no browser.
- [ ] Default output is human-readable: a short summary line (`31 notes served, 2 errors, 0 warnings`) followed by one line per issue grouped by category. `--json` emits the same shape as `/api/validation`.
- [ ] Exit codes: `0` clean, `1` warnings only, `2` any errors, `3` could not scan (bad path, IO failure).
- [ ] Document in `README.md` alongside the server/watch mode.

### 8.2 Claude Code skill

- [ ] `claude/skills/lore-weave-validate/SKILL.md` — trigger description ("check LoreWeave vault", "validate my notes", …) + invocation: shell out to `java -jar <…>/lore-weave-watch.jar check --json <vault>` and surface the result.
- [ ] `claude/skills/lore-weave-validate/README.md` — install instructions (copy the skill dir into `~/.claude/skills/`), typical trigger phrases, and how the skill locates the jar (recommend a stable path, e.g. `~/tools/lore-weave-watch/lore-weave-watch.jar`, overridable via an env var the SKILL.md documents).
- [ ] Manual end-to-end check: from a Claude Code session in a LoreWeave-shaped vault, ask for validation, verify the skill fires, returns structured output, and surfaces errors/warnings with their file paths.
- [ ] Keep the skill thin — no re-implementation of anything in the jar. If the jar's output format changes, update only the SKILL.md description, not the wrapping logic.

### 8.3 Wiring note

- [ ] Shared code path: both `watch` (server) and `check` (CLI) call into the same `IndexBuilder`. The check mode produces a single snapshot; the watch mode rebuilds on each poll. No duplicated validation logic.
- [ ] Tests: add a `CheckCliTest` that runs the subcommand against the fixture vaults (valid + invalid) and asserts exit codes + JSON shape.

## Future considerations

- **Server-Sent Events** for push updates instead of polling (once `/api/validation` is warm).
- **Click-to-open**: deep-link a file row into Obsidian via its `obsidian://open?vault=...&file=...` URI scheme.
- **Config file** at `<vault>/.loreweave/config.json` for port, poll interval, vault-override, severity filter defaults.
- **Shared library**: promote the vendored packages into a published `loreweave-core` Maven artifact so both this project and LoreWeave depend on the same release. Do this only once drift between the two starts to bite.
- **Dark-mode palette** that follows the browser's `prefers-color-scheme`.

---

## Working with the main LoreWeave repo

- Schema + validation changes originate in LoreWeave. When they happen, update the vendored copy here in the same release cycle and bump the recorded source commit in `COPYING_NOTES.md`.
- Any bug found in the parser should be fixed in LoreWeave first, then ported. This project intentionally has no independent parser fixes.
