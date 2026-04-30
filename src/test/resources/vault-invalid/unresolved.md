---
type: character
title: Linker
summary: Links to a note that does not exist.
schema_version: 1
---

Pointing at [[DoesNotExist]] on purpose.

And again at [[DoesNotExist]] — the duplicate is intentional. A real vault that
mentions the same broken target twice produces two byte-identical issues; the
watcher UI relies on receiving both so its diff-aware renderer is exercised
against duplicate keys (regression guard for the row-growth bug).
