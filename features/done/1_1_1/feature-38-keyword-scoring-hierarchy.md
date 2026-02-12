# Feature 38: Keyword Scoring Hierarchy

## Summary

Version 1.1.1 introduces a hierarchical keyword scoring system that weights keywords based on their structural location within documents. Keywords found in filenames and titles receive higher scores than those in body text, improving search relevance.

## User Story

**As an** AI agent searching Quarkus documentation  
**I want** search results ranked by keyword relevance and location  
**So that** documents most directly related to my query appear first

## Motivation

Not all keyword occurrences are equally relevant. A keyword in a document's title or filename indicates the document is primarily about that topic, while a keyword in body text may be a tangential mention. This hierarchical scoring improves search precision.

---

## Requirements

### Keyword Source Hierarchy

Keywords are extracted and weighted based on their source location:

| Priority | Source | Description | Weight | Multiplier |
|----------|--------|-------------|--------|------------|
| 1 | Filename | Keywords from the file name (without extension) | Highest | 10x |
| 2 | Document Title | H1 heading (AsciiDoc: `= Title`) | High | 8x |
| 3 | Section Title | H2 heading (AsciiDoc: `== Section`) | Medium | 5x |
| 4 | Subtitle | H3+ headings (AsciiDoc: `===` and lower) | Lower | 2x |
| 5 | Body | Regular paragraph text | Base | 1x |

### Weight Calculation

The final score for a keyword match is calculated as:

```
keyword_score = base_score × location_multiplier × frequency_factor
```

Where:
- `base_score`: Normalized relevance (0.0 - 1.0)
- `location_multiplier`: From hierarchy table above
- `frequency_factor`: `min(1.0 + log(count), 2.0)` to prevent keyword stuffing

### AsciiDoc Heading Detection

| AsciiDoc Pattern | Level | Example |
|------------------|-------|---------|
| `= Title` | H1 (Document Title) | `= Security Overview` |
| `== Section` | H2 (Section Title) | `== Authentication` |
| `=== Subsection` | H3 (Subtitle) | `=== OAuth2 Configuration` |
| `==== Deep Section` | H4 (Subtitle) | `==== Token Validation` |
| `===== Deeper` | H5+ (Subtitle) | `===== Custom Claims` |

---

## Detailed Requirements

### R1: Section Title Extraction

**Description:** Extract section titles from AsciiDoc documents and index them as keywords with section-level weight.

**Acceptance Criteria:**
- [x] H2 sections (`==`) extracted with their titles
- [x] Section keywords weighted at 5x multiplier
- [x] Section boundaries tracked for content association
- [x] Empty sections handled gracefully

### R2: Subtitle Extraction

**Description:** Extract H3+ headings and index them with subtitle-level weight.

**Acceptance Criteria:**
- [x] H3, H4, H5+ headings extracted
- [x] Subtitle keywords weighted at 2x multiplier
- [x] Heading level preserved in index for debugging

### R3: Filename Keyword Extraction

**Description:** Extract meaningful keywords from document filenames.

**Acceptance Criteria:**
- [x] File extension removed before extraction
- [x] Hyphens and underscores treated as word separators
- [x] Common stopwords filtered (e.g., "guide", "tutorial", "doc")
- [x] Filename keywords weighted at 10x multiplier

**Example:**
```
security-jwt-authentication.adoc
→ Keywords: ["security", "jwt", "authentication"]
→ Weight: 10x each
```

### R4: Document Title Extraction

**Description:** Extract the H1 document title and index with high weight.

**Acceptance Criteria:**
- [x] First `= Title` in document extracted
- [x] Title keywords weighted at 8x multiplier
- [x] Handles multi-word titles correctly
- [x] Handles titles with special characters

### R5: Compound Score Calculation

**Description:** When a keyword appears in multiple locations, calculate compound score.

**Acceptance Criteria:**
- [x] Same keyword in multiple locations uses highest weight (not sum)
- [x] Frequency factor applied after location weight
- [x] Final score normalized to 0.0 - 1.0 range for display
- [x] Raw scores preserved for ranking

**Example:**
```
Keyword "security" appears in:
- Filename: security-overview.adoc (10x)
- Title: "Security Overview" (8x)
- Body: 3 mentions (1x × frequency_factor)

Result: weight = 10x (highest), frequency = 1 + log(5) ≈ 1.7
```

### R6: Search Result Enrichment

**Description:** Include keyword context in search results.

**Acceptance Criteria:**
- [x] `matchedKeywords` array includes all matched keywords
- [x] Each matched keyword indicates its source location
- [x] Score reflects weighted calculation
- [x] Results sorted by weighted score descending

**Response Example:**
```json
{
  "matchedKeywords": [
    { "keyword": "security", "source": "filename", "weight": 10.0 },
    { "keyword": "oauth", "source": "section", "weight": 5.0 }
  ],
  "score": 0.95
}
```

---

## API Impact

### Affected Endpoints

| Endpoint | Impact |
|----------|--------|
| `GET /api/documents` | Search results sorted by weighted score |
| `GET /api/code-samples` | Code samples scored by context keywords |
| `GET /api/search` | Primary discovery sorted by weighted score |

### Index Schema Changes

The keyword scoring enhancement extends the existing normalized schema by adding `source` columns to track where keywords were found:

```sql
-- Extend existing file_keywords table
ALTER TABLE file_keywords ADD COLUMN source TEXT NOT NULL DEFAULT 'body';
-- source values: 'filename', 'title', 'body'

-- Extend existing section_keywords table  
ALTER TABLE section_keywords ADD COLUMN source TEXT NOT NULL DEFAULT 'body';
-- source values: 'section', 'subtitle', 'body'

-- Add indexes for efficient filtering by source
CREATE INDEX IF NOT EXISTS idx_file_keywords_source ON file_keywords(source);
CREATE INDEX IF NOT EXISTS idx_section_keywords_source ON section_keywords(source);
```

**Note:** Weights are computed at query time using the hierarchy table multipliers, not stored. This keeps the schema normalized while enabling flexible scoring.

---

## Technical Implementation Notes

### Stemming Considerations

- Keywords are stemmed before indexing (Porter Stemmer)
- Original form preserved for display in `matchedKeywords`
- Stemmed form used for matching

### Performance Optimization

- Pre-compute weights during indexing (not at query time)
- Store denormalized scores for fast retrieval
- Index on `(version, keyword)` for fast lookup

### Edge Cases

| Case | Handling |
|------|----------|
| Keyword only in body | Uses 1x weight |
| Empty filename keywords | Skip, rely on title |
| No H1 title | Use filename as title source |
| Duplicate keywords same location | Count once, increment frequency |

---

## Dependencies

- AsciiDoc parser for heading extraction
- Porter Stemmer for keyword normalization
- SQLite FTS for text matching (current)
- PostgreSQL tsvector (planned 1.2.0)

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Over-weighting filenames | Poor results for generic filenames | Filter common terms ("guide", "tutorial") |
| Stemmer inconsistency | Different results SQLite vs PostgreSQL | Accept minor differences, document behavior |
| Index size increase | Slower indexing | Batch inserts, optimize storage |

---

## Implementation Notes

**Implemented:** Thu Feb 12 2026

**Files Created:**
- `com.fvd.search.KeywordScoringConfig` - @ConfigMapping with weight multipliers
- `com.fvd.search.services.KeywordScorer` - Score calculation with frequency factor
- Unit tests for KeywordScorer

**Files Modified:**
- `KeywordIndexer` - Now extracts keywords with source metadata (filename, title, section, subtitle, body)
- `KeywordWeight`, `MatchedKeyword` - Added source field
- `SearchService` - Uses weighted scoring for result ranking
- Repository implementations - Support source column

**Weight Configuration:**
- Filename: 10x
- Title: 8x
- Section: 5x
- Subtitle: 2x
- Body: 1x

**Review Status:**
- Code Review: PASS
- Security Review: PASS
- 534 tests passing
