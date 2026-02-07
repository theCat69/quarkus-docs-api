# Feature 06: Background maintenance

This feature keeps cached docs fresh and reindexes keywords when upstream changes. It runs on a schedule using Quarkus background jobs.

## Cache freshness job

- For each cached version:
  - Fetch the latest index from GitHub contents API.
  - Compare SHA entries against `.cache/<version>/file_index.json`.
  - For changed files, re-fetch content and update cache.
  - Replace `file_index.json` with the latest raw response.

Upstream URLs:

- `https://api.github.com/repos/quarkusio/quarkus/contents/docs/src/main/asciidoc?ref=<quarkus_version>`
- `https://api.github.com/repos/quarkusio/quarkus/contents/<file_path>?ref=<quarkus_version>`

## Keyword reindex

- After index update, rebuild `keyword_index.json`.
- Reindexing should ignore code blocks and follow the scoring rules.

## Scheduling notes

- Refresh interval is configurable (hours).
- Failures should not remove existing cache.

## Internal interfaces

- `CacheRefreshJob.run()`
- `IndexService.getOrFetchIndex(version)`
- `KeywordIndexer.build(version)`

## Tasks

- [ ] Implement scheduled job for cache freshness.
- [ ] Compare old/new index entries by SHA.
- [ ] Refresh changed docs and replace `file_index.json`.
- [ ] Rebuild keyword index after changes.
