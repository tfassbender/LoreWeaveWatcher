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

- [x] Locate the jar at runtime via `WatchMain.class.getProtectionDomain().getCodeSource().getLocation()`. _(See `VaultLocator.ownCodeSourceLocation`.)_
- [x] Default rule: vault root = jar's parent-of-parent (i.e., the dir that contains the `.loreweave/` folder).
- [x] Sanity check: the detected root must contain at least one `.md` file. If not, walk upward looking for one. _(Recursive `.md` probe skips hidden dirs to match `VaultScanner` semantics; walks upward until a hit, else fails with exit code 3.)_
- [x] Override: `--vault <path>` flag for non-standard setups.
- [x] Log the detected root on startup so operators can spot misdetection. _(`Main` prints `vault: <abs-path>` before dispatching to watch/check.)_

## Phase 4 — HTTP server + JSON API

**Exit criteria**: `GET /api/validation` returns the full issue list + summary, with a fresh scan on each request.

- [x] Use `com.sun.net.httpserver.HttpServer`. Default port 5717 (watcher; the LoreWeave server uses 4717); fall back to OS-picked if bound. _(Bound to `127.0.0.1` only; `BindException` triggers retry on port 0 — see `WatchServer.bind`.)_
- [x] Endpoints:
  - `GET /` — embedded `index.html`. _(Minimal placeholder polling UI; phase 5 replaces the rendering.)_
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
- [x] Issues sorted by (severity desc, category asc, path asc, message asc) so the UI doesn't twitch between polls. _(See `ValidationApi.ISSUE_ORDER`.)_
- [x] Hand-rolled JSON writer is fine (no Jackson dep); all field types are strings/ints/arrays. _(`server/Json.java`, RFC 8259 escapes; ordered keys via `LinkedHashMap`.)_
- [x] Graceful shutdown on Ctrl-C: stop the HTTP server, exit 0. _(`Runtime.addShutdownHook` in `Main.runWatch` stops the server and counts down the latch.)_
- [x] Auto-launch the browser on startup via `java.awt.Desktop#browse(URI)` once the server is listening (fall back to printing the URL if `Desktop` is unsupported or headless). _(`WatchServer.openBrowser`.)_
- [x] Idle auto-shutdown: track the timestamp of the most recent `/api/validation` request. A background scheduler checks every second and exits cleanly if no poll has arrived within an idle threshold (default 10 s). Apply a startup grace window (~30 s) before the timeout becomes active so the browser has time to load. _(`server/IdleShutdown.java`; thresholds are constants.)_

> Vendored-code divergence required for this phase: extended `graph/Index` with `issues` and `notesExcluded`. See `COPYING_NOTES.md`.

## Phase 5 — Browser UI

**Exit criteria**: page auto-polls `/api/validation`, renders grouped issues, updates without flicker as files change.

- [x] Single `index.html` with vanilla JS, embedded as a classpath resource. _(Inline CSS + JS, no external assets; `vault` field added to `/api/validation` JSON so the top bar gets the path without a second endpoint.)_
- [x] Layout:
  - Top bar: vault path, last-scan timestamp (relative: "3 s ago"), `errors: N / warnings: N / notes served: N`. _(Sticky header; "scanned just now" / "scanned N s ago" / "N m" / "N h"; ticks once per second between polls.)_
  - Collapsible sections per category (errors first, then warnings). Each category expanded by default if it has issues. _(Click `<h2>` toggles `.collapsed`.)_
  - Each issue row: severity dot + path + message. Empty state: "No issues — looking good!". _(Hidden when issues > 0; shown when the issue list is empty.)_
- [x] Poll every ~1 s. Configurable via `?interval=<ms>` query param. _(Floor of 200 ms.)_
- [x] Diff-aware rendering: update DOM per-issue rather than full re-render, so scroll position is preserved. _(Stable per-issue keys `severity\0category\0path\0message`; sections + `<li>` reused across polls — verified in Playwright that the original DOM node and a user-collapsed section both survive a poll.)_
- [x] Offline / server-down state: banner at the top, stop polling, don't blow away the last rendered list. _(Banner shows last fetch error; polling continues at the configured interval so the page recovers automatically when the server is back; rendered list is left untouched.)_

## Phase 6 — Packaging + launchers

**Exit criteria**: drop-in install: drop the jar into `<vault>/.loreweave/`, run `java -jar`, see results.

- [x] `shadowJar` emits `lore-weave-watch.jar`. _(Confirmed by `./gradlew shadowJar`; output at `build/libs/lore-weave-watch.jar`.)_
- [~] Launcher scripts shipped alongside (skipped — `java -jar` is enough; users already have a JDK to run the watcher and we don't need OS-specific shims):
  - ~~`lore-weave-watch.bat` (Windows).~~
  - ~~`lore-weave-watch.sh` (Unix, chmod +x).~~
- [x] Browser auto-launch is already wired in phase 4; this phase just confirms it works on Windows + Linux. _(Verified end-to-end via Playwright on Windows; print-URL fallback path covered by smoke tests with `-Djava.awt.headless=true`.)_
- [x] README covers: place the jar under `<vault>/.loreweave/`, run via `java -jar`, note that `.loreweave/` is ignored by Obsidian by default. Also documents the manual-testing Gradle tasks `cloneTestVault` and `installToTestVault`. _(See `README.md` at the repo root.)_

## Phase 7 — Tests

**Exit criteria**: CI-green JUnit suite covering parser parity, validation, and an end-to-end server round-trip.

- [x] Unit tests for every ported parser/graph class — mirror the main repo's tests. _(Ported alongside the source in phase 2: `parser/{FrontmatterParser, HashtagExtractor, NoteAssembler, TitleResolver, WikiLinkExtractor}Test` and `graph/{IndexBuilder, LinkResolver, VaultScanner}Test`. Upstream's `Sync*`/`Search*`/`Related*` tests are intentionally not ported because the corresponding production classes weren't vendored — see `COPYING_NOTES.md`.)_
- [x] Server integration test: `HttpServer` against a fixture vault in `src/test/resources/`, hit `/api/validation`, assert the JSON shape and content. _(`WatchServerTest` covers `/api/validation`, `/api/config` GET/POST/validation-error, `/api/vault-paths`, `/`, 404, port fallback, idle-poll reset, and ignore-path filtering.)_
- [x] Launcher manual smoke test documented in the README. _(Phase 6 skipped launcher scripts in favour of `java -jar` / double-click; the cross-platform risk is effectively zero — pure JDK HTTP + file IO + `Desktop.browse` with a print-URL fallback. End-to-end run verified on Windows via Playwright; Linux/macOS not separately smoke-tested but expected to work via the same code path.)_

## Phase 8 — One-shot CLI mode + Claude Code skill

Moved here from the main LoreWeave repo (was its original phase 10). The skill wraps a headless `check` mode of this same jar, so the server UI and the scripted validator share every line of parser and graph code.

**Exit criteria**: `java -jar lore-weave-watch.jar check <vault-path>` prints a validation report and exits with a code that distinguishes clean / warnings-only / errors. A Claude Code skill copied into `~/.claude/skills/` invokes that command from chat and returns the structured output.

### 8.1 Headless `check` subcommand

- [x] Add a `check` subcommand to the same `Main` dispatcher (so we have `lore-weave-watch.jar` for the default watch server and `lore-weave-watch.jar check …` for the one-shot mode — one artifact, two modes). _(Dispatched in `Main.runCheck`; implementation in `cli/CheckCommand`.)_
- [x] Arguments: positional `<vault-path>` (defaults to the same auto-detect used in phase 3 if omitted); `--json` for structured output; `--severity=errors|warnings|all` (default `all`). _(Already wired in `Args.parse`; check now consumes them.)_
- [x] Runs `IndexBuilder.build(path)` once, formats the `ValidationReport`, writes to stdout, exits. No HTTP server, no browser. _(Single shared call into `IndexBuilder.build(vault, ignorePredicate)`; `ignore_paths` from the on-disk config are honoured so check and watch agree on scope.)_
- [x] Default output is human-readable: a short summary line (`31 notes served, 2 errors, 0 warnings`) followed by one line per issue grouped by category. `--json` emits the same shape as `/api/validation`. _(JSON path reuses `ValidationApi.render`.)_
- [x] Exit codes: `0` clean, `1` warnings only, `2` any errors, `3` could not scan (bad path, IO failure). _(Exit 2 is set whenever real errors exist regardless of `--severity`, so CI can't be silenced into thinking a broken vault is fine.)_
- [x] Document in `README.md` alongside the server/watch mode. _(Added "Headless `check` mode" section with flag table and exit-code table.)_

### 8.2 Claude Code skill

- [x] `claude/skills/lore-weave-validate/SKILL.md` — trigger description and invocation. _(Documents the `LWW_JAR` env override and the auto-detection fallback.)_
- [x] `claude/skills/lore-weave-validate/README.md` — install instructions, typical trigger phrases, jar-location guidance.
- [~] Manual end-to-end check from a Claude Code session — _deferred. The skill is thin and the underlying CLI is covered by `CheckCommandTest` end-to-end (CLI → JSON output → exit code), so the remaining risk is purely "does the skill prompt fire". Will verify ad-hoc on first real use._
- [x] Keep the skill thin — no re-implementation of anything in the jar. _(The skill body is description + a single shell invocation, no logic.)_

### 8.3 Wiring note

- [x] Shared code path: both `watch` (server) and `check` (CLI) call into the same `IndexBuilder.build(vault, ignorePredicate)`. The check mode produces a single snapshot; the watch mode rebuilds on each poll. No duplicated validation logic.
- [x] Tests: `CheckCommandTest` runs the subcommand against the fixture vaults (valid + invalid) and asserts exit codes + JSON shape, including the `--severity` filtering branches.

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
