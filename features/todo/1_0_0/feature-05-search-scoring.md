# Feature 05: Search and scoring

This feature exposes keyword search over cached docs. It uses the keyword index to return top matches with scores, for both file-level and section-level queries.

## File search

- Input: `version`, `keywords`.
- For each keyword:
  - Find matching files in the index.
  - Sum scores across keywords.
  - Boost if more than one keyword matched the same file.
- Return top 5-10 results with scores.

## Section search

- Input: `version`, `keywords`, `filePaths`.
- Limit candidates to the provided file paths.
- Sum scores across keywords per section.
- Return top 3-5 sections with scores and line ranges.

## Response shape

- File search response: `{ "results": [ { "path": "...", "score": 12.4 } ] }`
- Section search response:
  `{ "results": [ { "path": "...", "section": "...", "start": 12, "end": 42, "score": 9.2 } ] }`

## Internal interfaces

- `SearchService.searchFiles(version, keywords[])`
- `SearchService.searchSections(version, keywords[], filePaths[])`

## Tasks

- [ ] Implement file-level score aggregation and multi-keyword boost.
- [ ] Implement section-level search restricted to `filePaths`.
- [ ] Enforce result limits (5-10 files, 3-5 sections).
- [ ] Return stable, sorted results by descending score.
