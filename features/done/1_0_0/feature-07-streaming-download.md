# Feature 07: Streaming download and extraction

This feature downloads the Quarkus repo zip in a streamed way and extracts only the asciidoc docs subtree. It is used both for background tasks and for lazy initialization when keyword search is requested and a full cache is missing.

## When to stream

- Background maintenance needs full cache.
- Keyword search endpoint is called and `.cache/<version>/docs` is missing or incomplete.

## Extraction scope

- Download: Quarkus repo zip for the target version.
- Extract only: `docs/src/main/asciidoc`.
- Output: `.cache/<version>/docs` with matching relative paths.

Upstream URL:

- `https://www.github.com/quarkusio/quarkus/archive/refs/heads/<quarkus_version>.zip`

## Operational notes

- Use streaming to avoid memory spikes on large zips.
- Ensure partial extractions do not overwrite valid cache.
- Allow retry/resume behavior without corrupting the cache.

## Internal interfaces

- `ZipDownloadService.streamAndExtract(version)`
- `DocStore.write(path, content)`

## Tasks

- [x] Stream zip download to disk or stream extractor.
- [x] Extract only `docs/src/main/asciidoc` into `.cache/<version>/docs`.
- [x] Trigger streaming on keyword search when cache is missing.
- [x] Add safeguards for partial or failed extraction.
