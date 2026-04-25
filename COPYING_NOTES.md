# Vendored code provenance

The following packages are vendored from [LoreWeave](https://github.com/tfassbender/LoreWeave):

- `com.tfassbender.loreweave.watch.domain` — from `domain/`
- `com.tfassbender.loreweave.watch.parser` — from `parsing/`
- `com.tfassbender.loreweave.watch.graph` — from `graph/{Index, IndexedNote, IndexBuilder, LinkResolver, ResolvedLink, ValidationReport, VaultScanner}`

Parser bug fixes originate in LoreWeave first, then get ported here. Never fix parser bugs independently in this repo.

## Source commit

| Package set | Source commit | Ported on |
|---|---|---|
| `domain/`, `parsing/` (→ `parser/`), `graph/{Index,IndexBuilder,IndexedNote,LinkResolver,ResolvedLink,ValidationReport,VaultScanner}` + matching tests + fixture vaults | [`1c85eb1f`](https://github.com/tfassbender/LoreWeave/commit/1c85eb1f84b4039675c523878ea98cab6f76a983) | 2026-04-25 |

## Renames

- `com.tfassbender.loreweave.parsing` → `com.tfassbender.loreweave.watch.parser` (per CLAUDE.md target package layout).
- `com.tfassbender.loreweave.{domain,graph}` → `com.tfassbender.loreweave.watch.{domain,graph}`.

## Excluded from the port

These graph classes were intentionally not vendored — they belong to the server/git side of LoreWeave and have no role in the local watcher:

- `LastSync`, `RelatedService`, `SearchService`, `SyncInProgressException`, `SyncService`
- Their corresponding tests (`SyncServiceTest`, `SyncServiceConcurrencyTest`, `SearchServiceTest`, `RelatedServiceTest`)
- All of `cli/`, `config/`, `git/`, `health/`, `rest/` (Quarkus REST + JGit + scheduler glue)
