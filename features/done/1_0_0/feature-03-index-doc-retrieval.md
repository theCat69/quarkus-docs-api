# Feature 03: Indexing and doc retrieval

This feature handles the GitHub calls for Quarkus docs, caching the index and individual asciidoc files. The index is stored as-is; documents are stored under the cache docs tree. This also includes full repo zip extraction to populate the cache quickly.

## Index retrieval

- Use the GitHub contents API to fetch the docs index for a version.
- Store the raw JSON response in `.cache/<version>/file_index.json` without transformation.

Upstream URL:

- `https://api.github.com/repos/quarkusio/quarkus/contents/docs/src/main/asciidoc?ref=<quarkus_version>`

## Single document retrieval

- Check `.cache/<version>/docs/<file_path>` first.
- If missing, call GitHub contents API for the specific file path.
- Persist the decoded asciidoc content to the cache.

Upstream URL:

- `https://api.github.com/repos/quarkusio/quarkus/contents/<file_path>?ref=<quarkus_version>`

## Full zip download and extraction

- Download Quarkus repo zip for a version.
- Extract only `docs/src/main/asciidoc`.
- Place extracted files into `.cache/<version>/docs` while preserving paths.

Upstream URL:

- `https://www.github.com/quarkusio/quarkus/archive/refs/heads/<quarkus_version>.zip`

## Internal interfaces

- `IndexService.getOrFetchIndex(version)`
- `DocService.getOrFetchDoc(version, path)`
- `ZipDownloadService.extractDocsSubfolder(version)`

## Tasks

- [x] Fetch and store raw file index from GitHub contents API.
- [x] Fetch, decode, and cache a single asciidoc file.
- [x] Implement zip extraction for `docs/src/main/asciidoc`.
- [x] Ensure extracted docs are written under `.cache/<version>/docs`.
