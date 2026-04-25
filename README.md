# lore-weave-watch

A live validation dashboard for [LoreWeave](https://github.com/tfassbender/LoreWeave) Obsidian vaults. Drop the fat jar into `<vault>/.loreweave/`, launch it, and a browser tab shows what needs fixing — updated within a second of every save in Obsidian. No commits, no server round-trip.

> Sibling project to [LoreWeave](https://github.com/tfassbender/LoreWeave) (the REST API server) and [LoreWeaveTestVault](https://github.com/tfassbender/LoreWeaveTestVault) (the fixture vault). This repo runs **locally** on the author's machine and reads files directly from disk.

## Quickstart

1. Place `lore-weave-watch.jar` in your vault under `.loreweave/`:

   ```
   <my-vault>/
     .loreweave/
       lore-weave-watch.jar
     characters/
     factions/
     …
   ```

2. Launch:

   ```bash
   java -jar <my-vault>/.loreweave/lore-weave-watch.jar
   ```

   On Windows, macOS, and most Linux desktops you can also just double-click `lore-weave-watch.jar` if your system has Java associated with `.jar` files — same effect.

3. The dashboard opens in your default browser at <http://127.0.0.1:5717/>. (If `Desktop.browse` is unavailable — headless box, no GUI — the URL is printed instead.)

The vault root is auto-detected as the parent-of-parent of the jar's location, so step 2 is the only thing you need to remember.

## What you'll see

- A sticky top bar with the vault path, a green/red status chip, and live counts (errors / warnings / served / excluded).
- Collapsible sections per validation category — errors first, then warnings.
- Per-issue and per-category eye-slash buttons that hide noise for the current session; a "show N hidden" pill restores them.
- A gear button opens a settings dialog (theme, auto-open browser, poll interval, idle shutdown, ignore paths).

## Configuration

Settings are persisted to `<vault>/.loreweave/lore-weave-watch.json` and hot-reload on save. The settings dialog (gear icon in the top bar) is the easiest entry point; the file is plain JSON if you'd rather edit it by hand:

```json
{
  "theme": "dark",
  "auto_open_browser": true,
  "poll_interval_ms": 1000,
  "idle_shutdown": {
    "enabled": true,
    "threshold_ms": 10000,
    "grace_ms": 30000
  },
  "ignore_paths": []
}
```

| Key | Effect |
|---|---|
| `theme` | `"dark"` (default) or `"light"`. The toggle button in the top bar mirrors this. |
| `auto_open_browser` | Whether to call `Desktop.browse(URI)` on startup. Set `false` for terminal users who already have a tab open. |
| `poll_interval_ms` | Dashboard polls `/api/validation` at this rate. URL `?interval=<ms>` overrides per-tab. |
| `idle_shutdown.enabled` / `threshold_ms` / `grace_ms` | Server exits when no poll has arrived in `threshold_ms` past a `grace_ms` startup window. Validation rule: `threshold_ms ≥ poll_interval_ms + 5000`. |
| `ignore_paths` | Files or directory prefixes the scanner skips (e.g. `"drafts/"`, `"scratch.md"`). |

### CLI flags

| Flag | Default | Notes |
|---|---|---|
| `--vault <path>` | auto-detect | Override the auto-detected vault root. |
| `--port <n>` | `5717` | Falls back to an OS-picked port if bound. |
| `-h`, `--help` | — | Prints help and exits. |
| `-v`, `--version` | — | Prints version and exits. |

A headless `check` subcommand (one-shot validation, exits with `0`/`1`/`2`/`3` for clean/warnings/errors/scan-failure) is on the roadmap for phase 8.

## Obsidian compatibility

Obsidian ignores any directory whose name starts with a dot, so `.loreweave/` is invisible to the Obsidian client by default. If you have changed Obsidian's "Files and links → Detect all file extensions" / excluded-files configuration, add `.loreweave/` to the excluded list to keep the jar out of search and graph views.

## Building from source

Requires JDK 21.

```bash
./gradlew shadowJar          # builds build/libs/lore-weave-watch.jar
./gradlew test               # runs the JUnit suite
java -jar build/libs/lore-weave-watch.jar
```

### Manual testing against a real vault

Two convenience tasks check out the public [LoreWeaveTestVault](https://github.com/tfassbender/LoreWeaveTestVault) and install the freshly-built jar into it:

```bash
./gradlew cloneTestVault     # clone or fast-forward into ./test-vault/
./gradlew installToTestVault # build shadow jar and copy into ./test-vault/.loreweave/
java -jar test-vault/.loreweave/lore-weave-watch.jar
```

`./test-vault/` is gitignored. The fixtures under `_problems/` in that vault exercise every validation category lore-weave-watch reports.

## License

[MIT](LICENSE).

The dashboard uses [Font Awesome 6 Free](https://fontawesome.com/) icons (CC BY 4.0), loaded from cdnjs at runtime — see the comment at the top of `index.html` for attribution.

## Related repositories

- [LoreWeave](https://github.com/tfassbender/LoreWeave) — REST API server, schema, parser of record. Bug fixes to the parser originate there and get ported here.
- [LoreWeaveTestVault](https://github.com/tfassbender/LoreWeaveTestVault) — the fixture vault used by both projects.
