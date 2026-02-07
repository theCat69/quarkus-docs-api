# Feature 04: Keyword indexing

This feature builds a keyword index from cached asciidoc files. The index supports both file-level search and section-level search, with scoring to rank results.

## Parsing rules

- Parse asciidoc text.
- Ignore code blocks when collecting keywords.
- Tokenize keywords individually (simple word split with normalization).

## File-level keyword index

- For each doc file:
  - Count keyword occurrences.
  - Boost filename matches by +10.
- Store per-file keyword list with scores.

## Section-level index

- Detect section boundaries and titles.
- For each section:
  - Record start and end line numbers.
  - Collect keywords within the section.
  - Boost section title keywords.

## Output format (conceptual)

```json
{
  "files": [
    {
      "path": "guides/security-oidc.adoc",
      "keywords": [{"word":"oidc","score":15}],
      "sections": [
        {"title":"Config","start":120,"end":168,"keywords":[{"word":"issuer","score":6}]}
      ]
    }
  ]
}
```

## Internal interfaces

- `KeywordIndexer.build(version)`
- `KeywordIndexStore.read(version)` / `KeywordIndexStore.write(version, json)`

## Tasks

- [x] Implement asciidoc parser with code-block exclusion.
- [x] Build per-file keyword scores with filename boost.
- [x] Build section index with start/end lines and title boost.
- [x] Persist keyword index to `.cache/<version>/keyword_index.json`.
