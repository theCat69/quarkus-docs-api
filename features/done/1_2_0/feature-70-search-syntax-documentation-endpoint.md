# Feature 70: Search Syntax Documentation Endpoint

> **Dependencies**: None. This is a self-contained, static-response endpoint. No dependency on cached data, indexes, or external services.

## Summary

AI agents consuming this API through the MCP server have no programmatic way to discover what query syntax the search engine supports. They cannot know that keywords are stemmed, that prefix matching is applied at 0.8x score, that stop words are filtered, or that quoted phrases and boolean operators are **not** supported. This leads to ineffective queries (e.g., `"rest endpoint"` with quotes, `security AND oidc`). This feature exposes a `GET /api/search/syntax` endpoint returning a machine-readable JSON document describing all search capabilities, tokenization rules, scoring behavior, stop words, and query examples.

## User Story

As an **AI agent formulating search queries**, I want to retrieve a machine-readable description of the search engine's syntax, capabilities, and limitations so that I can construct effective queries and avoid unsupported syntax patterns.

## Motivation

### Current Problem

- AI agents send queries like `"rest endpoint"` (quoted phrase) — the quotes are treated as literal characters, not phrase delimiters
- Agents try `security AND oidc` — "AND" is treated as a keyword, not a boolean operator
- Agents don't know that `security` is stemmed to `secur` and will also match `securing`, `secured`, `securities`
- Agents don't know that `the`, `how`, `is` are stop words and will be silently dropped
- Agents don't know that multi-keyword queries get a 1.5x boost, so `rest security` scores higher than two separate single-keyword queries
- No API-discoverable documentation exists — agents must rely on out-of-band instructions

### Desired Behavior

`GET /api/search/syntax` returns a comprehensive, structured JSON response describing exactly how the search engine processes queries. Agents parse this once (or periodically) and adapt their query construction accordingly.

---

## Requirements

### R1: Create `GET /api/search/syntax` Endpoint

**New file:** `src/main/java/com/fvd/api/resources/SearchSyntaxResource.java`

Create a new JAX-RS resource at `/api/search/syntax` that returns a static `SearchSyntaxResponse` DTO. This endpoint:

- Is `@GET` with `@Produces(MediaType.APPLICATION_JSON)`
- Requires no query parameters (no `version`, no authentication)
- Returns a static response (no database or cache access needed)
- Uses `@Tag(name = "Search")` to group with the existing search endpoint in OpenAPI
- Uses `@Operation` and `@APIResponse` annotations for OpenAPI documentation

```java
@Path("/api/search/syntax")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Search", description = "Quick discovery search returning lightweight references")
public class SearchSyntaxResource {

    @GET
    @Operation(
            summary = "Search syntax documentation",
            description = "Returns machine-readable documentation of search query syntax, " +
                    "supported features, scoring behavior, and examples. " +
                    "AI agents should call this endpoint to understand how to construct effective queries."
    )
    @APIResponse(
            responseCode = "200",
            description = "Search syntax documentation",
            content = @Content(schema = @Schema(implementation = SearchSyntaxResponse.class))
    )
    public SearchSyntaxResponse getSyntax() {
        return SearchSyntaxResponse.INSTANCE;
    }
}
```

**Design decision — separate resource vs. adding to `SearchResource`:** A separate resource keeps the static documentation concern isolated from the dynamic search logic. The `SearchResource` class requires `QuickSearchService`, `CacheService`, and `SubjectDeriver` injected — none of which are needed here. A dedicated resource avoids unnecessary CDI overhead and follows single-responsibility.

**Design decision — `/api/search/syntax` vs. `/api/meta`:** The `/api/search/syntax` path is more discoverable and semantically scoped. A generic `/api/meta` endpoint would mix concerns (search syntax, API versioning, health info). If `/api/meta` is needed later for other purposes, it can link to `/api/search/syntax`.

### R2: Create `SearchSyntaxResponse` DTO

**New file:** `src/main/java/com/fvd/api/dto/SearchSyntaxResponse.java`

A DTO with public fields and Lombok annotations, following the project DTO pattern. The response is built as a static singleton since all data is constant.

The DTO should be composed of nested static inner classes for structured sections:

```java
@NoArgsConstructor
@AllArgsConstructor
public class SearchSyntaxResponse {

    public TokenizationInfo tokenization;
    public StemmingInfo stemming;
    public ScoringInfo scoring;
    public StopWordsInfo stopWords;
    public FuzzyMatchingInfo fuzzyMatching;
    public SupportedFeaturesInfo supported;
    public UnsupportedFeaturesInfo unsupported;
    public List<FilterInfo> filters;
    public List<QueryExample> examples;
    public List<String> tips;

    public static final SearchSyntaxResponse INSTANCE = buildInstance();

    private static SearchSyntaxResponse buildInstance() { ... }
}
```

### R3: Response Schema

The JSON response must include the following sections. Each section is a nested object or array.

#### Full Example Response

```json
{
  "tokenization": {
    "description": "Keywords are split by whitespace. Each token is lowercased and stemmed independently.",
    "separator": "whitespace (spaces, tabs)",
    "caseSensitive": false,
    "minTokenLength": 3,
    "rules": [
      "Input is split on whitespace into individual tokens",
      "Each token is converted to lowercase",
      "Stop words are removed before processing",
      "Each remaining token is stemmed using suffix-stripping rules",
      "Stemmed tokens are matched against the keyword index"
    ]
  },
  "stemming": {
    "description": "A simple English suffix-stripping stemmer groups related word forms. The stemmer is deterministic and consistent, not linguistically perfect.",
    "algorithm": "Custom suffix-stripping (Porter-like)",
    "examples": [
      { "input": "security", "stemmed": "secur", "alsoMatches": ["securing", "secured", "securities"] },
      { "input": "configuration", "stemmed": "configur", "alsoMatches": ["configurable", "configured", "configuring"] },
      { "input": "running", "stemmed": "run", "alsoMatches": ["runner", "runs"] },
      { "input": "authentication", "stemmed": "authent", "alsoMatches": ["authenticate", "authenticated"] },
      { "input": "dependency", "stemmed": "depend", "alsoMatches": ["dependencies", "dependent"] },
      { "input": "injection", "stemmed": "inject", "alsoMatches": ["injecting", "injectable"] }
    ],
    "suffixesStripped": ["ation", "tion", "sion", "ment", "ness", "able", "ible", "ous", "ive", "ity", "ful", "less", "ing", "ed", "ly", "er", "est", "es", "s"]
  },
  "scoring": {
    "description": "Documents are scored based on keyword match location, match type, and query structure.",
    "matchTypes": [
      { "type": "exact", "description": "Stemmed query exactly matches indexed keyword", "scoreMultiplier": 1.0 },
      { "type": "prefix", "description": "Indexed keyword starts with stemmed query", "scoreMultiplier": 0.8 }
    ],
    "locationWeights": [
      { "location": "filename", "weight": 10.0, "description": "Keyword appears in the document filename" },
      { "location": "title", "weight": 8.0, "description": "Keyword appears in the document title (H1)" },
      { "location": "section", "weight": 5.0, "description": "Keyword appears in a section heading (H2)" },
      { "location": "subtitle", "weight": 2.0, "description": "Keyword appears in a subtitle (H3+)" },
      { "location": "body", "weight": 1.0, "description": "Keyword appears in body text" }
    ],
    "multiKeywordBoost": {
      "multiplier": 1.5,
      "description": "Queries with 2+ keywords receive a 1.5x score boost when multiple keywords match"
    },
    "frequencyFactor": {
      "formula": "min(1.0 + log(count), 2.0)",
      "description": "Repeated occurrences of a keyword increase score logarithmically, capped at 2.0x"
    }
  },
  "stopWords": {
    "description": "Stop words are common words automatically removed from queries before searching. A query containing only stop words returns HTTP 400.",
    "behavior": "Silently removed from query. If all keywords are stop words, the API returns 400 Bad Request.",
    "words": [
      "a", "an", "and", "the", "how", "does", "do", "is", "are", "was",
      "were", "what", "which", "who", "when", "where", "why", "in", "on",
      "at", "to", "for", "with", "from", "by", "of", "about", "explain",
      "show", "me", "work", "works", "working", "please", "your"
    ]
  },
  "fuzzyMatching": {
    "description": "Fuzzy matching is used only for section title lookups (not for general keyword search). It combines Levenshtein similarity, substring containment, and word overlap to find the best matching section title.",
    "appliesTo": "Section title search only (GET /api/documents with sectionTitle parameter)",
    "notAppliedTo": "Keyword search (GET /api/search, GET /api/documents with keywords parameter)",
    "algorithm": "Weighted combination: Levenshtein (0.4) + Containment (0.35) + Word Overlap (0.25)",
    "defaultThreshold": 0.3
  },
  "supported": {
    "features": [
      { "feature": "Space-separated keywords", "description": "Multiple keywords separated by spaces: 'security oidc'", "example": "security oidc" },
      { "feature": "Stemming", "description": "Words are reduced to stems for broader matching: 'configuring' matches 'configuration'", "example": "configure" },
      { "feature": "Prefix matching", "description": "Short query stems match longer indexed keywords at 80% score", "example": "sec" },
      { "feature": "Multi-keyword boost", "description": "Queries with 2+ keywords get 1.5x score boost", "example": "rest security" },
      { "feature": "Subject filter", "description": "Filter results by documentation subject category", "example": "keywords=security&subject=security" },
      { "feature": "Extension filter", "description": "Filter results by Quarkus extension name", "example": "keywords=config&extension=quarkus-core" },
      { "feature": "Pagination", "description": "Use limit and offset parameters to paginate results", "example": "keywords=security&limit=10&offset=20" }
    ]
  },
  "unsupported": {
    "description": "The following query syntax patterns are NOT supported and will be treated as literal keyword text.",
    "features": [
      { "syntax": "\"quoted phrases\"", "description": "Quotes are treated as literal characters, not phrase delimiters. Use space-separated keywords instead.", "workaround": "Use individual keywords: 'rest endpoint' instead of '\"rest endpoint\"'" },
      { "syntax": "AND / OR / NOT", "description": "Boolean operators are treated as regular keywords (and 'and' is a stop word that gets removed).", "workaround": "Use space-separated keywords for AND-like behavior. OR/NOT are not supported." },
      { "syntax": "* or ? wildcards", "description": "Glob/wildcard patterns are not supported. Characters are treated as literals.", "workaround": "Rely on stemming and prefix matching for broader matches." },
      { "syntax": "field:value", "description": "Field-specific search (e.g., 'title:security') is not supported.", "workaround": "Use the 'subject' or 'extension' query parameters for filtering." },
      { "syntax": "+required -excluded", "description": "Required/excluded term modifiers are not supported.", "workaround": "All keywords are implicitly searched. Use subject/extension filters to narrow results." },
      { "syntax": "~ fuzzy operator", "description": "Tilde-based fuzzy search syntax is not supported for keyword search.", "workaround": "Stemming provides automatic fuzzy-like matching for word variants." }
    ]
  },
  "filters": [
    { "parameter": "version", "description": "Quarkus version branch or tag", "default": "main", "example": "3.17" },
    { "parameter": "subject", "description": "Documentation subject category filter", "default": null, "example": "security" },
    { "parameter": "extension", "description": "Quarkus extension name filter", "default": null, "example": "quarkus-resteasy-reactive" },
    { "parameter": "limit", "description": "Maximum number of results to return", "default": "20", "example": "10" },
    { "parameter": "offset", "description": "Number of results to skip for pagination", "default": "0", "example": "20" }
  ],
  "examples": [
    { "query": "security oidc", "description": "Search for documents about security and OIDC. Both keywords are stemmed and searched. Multi-keyword boost applies." },
    { "query": "rest endpoint", "description": "Search for REST endpoint documentation. 'rest' and 'endpoint' are searched independently." },
    { "query": "configure datasource", "description": "'configure' is stemmed to 'configur', matching 'configuration', 'configurable', etc. 'datasource' is searched as-is." },
    { "query": "hibernate orm", "description": "Search for Hibernate ORM documentation. Use with extension=quarkus-hibernate-orm for more precise results." },
    { "query": "grpc", "description": "Single keyword search. No multi-keyword boost, but still benefits from stemming and prefix matching." }
  ],
  "tips": [
    "Use specific, meaningful keywords — avoid generic terms like 'how', 'what', 'use'",
    "Prefer root word forms: 'config' instead of 'configuration' (stemming helps, but shorter roots improve prefix matching)",
    "Combine 2-3 keywords for best results — multi-keyword queries get a 1.5x score boost",
    "Use the 'subject' parameter to narrow results to a specific documentation category",
    "Use the 'extension' parameter to filter results by Quarkus extension",
    "Do not use quotes, boolean operators, or wildcard characters — they are treated as literal text",
    "If your query returns no results, try fewer or broader keywords",
    "Stop words (a, the, is, how, etc.) are automatically removed — no need to include them"
  ]
}
```

### R4: Populate Stop Words from `StopWords.DEFAULT`

The stop words list in the response should be sourced from `com.fvd.common.StopWords.DEFAULT` rather than hardcoded in the DTO, to keep the response consistent with actual behavior. This is the one dynamic element.

**Implementation approach:** The `SearchSyntaxResponse.buildInstance()` method reads `StopWords.DEFAULT` at class-load time and populates the `stopWords.words` list. Since `StopWords.DEFAULT` is a static constant `Set<String>`, this requires no CDI injection — just a direct static reference.

```java
private static SearchSyntaxResponse buildInstance() {
    // ...
    stopWordsInfo.words = StopWords.DEFAULT.stream().sorted().toList();
    // ...
}
```

### R5: OpenAPI Annotations

The endpoint and response DTO must have proper OpenAPI annotations:

- `@Tag(name = "Search")` on the resource class to group with existing search endpoints
- `@Operation(summary, description)` on the GET method
- `@APIResponse(responseCode = "200")` with schema reference
- `@Schema(description = ...)` on key response fields if needed for clarity

---

## Implementation Notes

### Static Response — No CDI Required

The response is entirely static (or computed once at class-load time from constants). The resource class does not need `@RequiredArgsConstructor` or any injected dependencies. This makes it the simplest possible endpoint.

### Why a Singleton DTO?

The response never changes at runtime. Building it once as `SearchSyntaxResponse.INSTANCE` avoids per-request object allocation. Jackson serializes the singleton on each request.

### Nested DTOs — Inner Classes vs. Top-Level

The nested info objects (`TokenizationInfo`, `StemmingInfo`, etc.) should be **public static inner classes** of `SearchSyntaxResponse` to keep the file self-contained. They are not reused elsewhere. Each should use `@NoArgsConstructor` and `@AllArgsConstructor` for Jackson compatibility.

### Why Not Include This in `/api/catalog`?

The catalog endpoint returns version-specific data (subjects, extensions). Search syntax documentation is version-independent and conceptually different. Mixing them would conflate metadata concerns.

### No `version` Parameter

Search syntax is the same across all Quarkus documentation versions. The endpoint accepts no parameters.

---

## Tasks

- [ ] Create `SearchSyntaxResponse` DTO in `com.fvd.api.dto` with all nested info classes (see R3 for full schema)
- [ ] Populate `SearchSyntaxResponse.INSTANCE` static singleton with all search syntax data
- [ ] Source `stopWords.words` from `StopWords.DEFAULT` (sorted alphabetically) rather than hardcoding
- [ ] Create `SearchSyntaxResource` in `com.fvd.api.resources` with `GET /api/search/syntax`
- [ ] Add `@Tag(name = "Search")`, `@Operation`, and `@APIResponse` OpenAPI annotations
- [ ] Add integration test: `GET /api/search/syntax` returns 200 with valid JSON
- [ ] Add integration test: response contains `tokenization`, `stemming`, `scoring`, `stopWords`, `supported`, `unsupported`, `examples`, `tips` top-level fields
- [ ] Add integration test: `stopWords.words` contains known stop words ("a", "the", "and", "is")
- [ ] Add integration test: `scoring.matchTypes` contains "exact" and "prefix" entries
- [ ] Add integration test: `unsupported.features` lists quoted phrases and boolean operators
- [ ] Add integration test: `stemming.examples` contains at least 3 entries with `input`, `stemmed`, `alsoMatches` fields
- [ ] Add unit test: `SearchSyntaxResponse.INSTANCE` is not null and has all sections populated
- [ ] Add unit test: `stopWords.words` list is sorted alphabetically
- [ ] Add unit test: `stopWords.words` matches `StopWords.DEFAULT` contents exactly
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/search/syntax` returns HTTP 200 with `Content-Type: application/json`
2. Response contains all top-level sections: `tokenization`, `stemming`, `scoring`, `stopWords`, `fuzzyMatching`, `supported`, `unsupported`, `filters`, `examples`, `tips`
3. `stopWords.words` list matches `StopWords.DEFAULT` and is sorted alphabetically
4. `stemming.examples` includes at least 3 examples with `input`, `stemmed`, and `alsoMatches` fields
5. `scoring.matchTypes` documents exact (1.0x) and prefix (0.8x) match types
6. `scoring.locationWeights` documents all 5 location weights (filename, title, section, subtitle, body)
7. `unsupported.features` explicitly lists: quoted phrases, boolean operators, wildcards, field-specific queries
8. `supported.features` documents: space-separated keywords, stemming, prefix matching, multi-keyword boost, subject filter, extension filter, pagination
9. `examples` contains at least 3 practical query examples with descriptions
10. `tips` contains at least 5 actionable tips for query construction
11. The endpoint requires no query parameters and no authentication
12. The endpoint appears under the "Search" tag in OpenAPI documentation
13. `./gradlew test` passes with zero failures

---

## Test Scenarios

### Integration Tests (`SearchSyntaxResourceTest`)

| # | Test Method | Description | Assertion |
|---|-------------|-------------|-----------|
| 1 | `shouldReturnSearchSyntaxDocumentation` | `GET /api/search/syntax` | Status 200, body has `tokenization`, `stemming`, `scoring`, `stopWords`, `supported`, `unsupported`, `examples`, `tips` |
| 2 | `shouldReturnStopWordsList` | Stop words list is populated | `stopWords.words` is non-empty, contains "a", "the", "and", "is" |
| 3 | `shouldReturnScoringMatchTypes` | Match types documented | `scoring.matchTypes` contains entries with type "exact" and "prefix" |
| 4 | `shouldReturnLocationWeights` | Location weights documented | `scoring.locationWeights` has 5 entries, includes "filename" with weight 10.0 |
| 5 | `shouldReturnUnsupportedFeatures` | Unsupported syntax documented | `unsupported.features` has entries for quoted phrases, boolean operators, wildcards |
| 6 | `shouldReturnStemmingExamples` | Stemming examples provided | `stemming.examples` has >= 3 entries, each with `input`, `stemmed`, `alsoMatches` |
| 7 | `shouldReturnQueryExamples` | Query examples provided | `examples` has >= 3 entries, each with `query` and `description` |
| 8 | `shouldReturnTips` | Tips provided | `tips` has >= 5 entries, all non-empty strings |
| 9 | `shouldReturnFilters` | Filter parameters documented | `filters` has entries for version, subject, extension, limit, offset |
| 10 | `shouldReturnFuzzyMatchingInfo` | Fuzzy matching scope documented | `fuzzyMatching.appliesTo` mentions section title search |

### Unit Tests (`SearchSyntaxResponseTest`)

| # | Test Method | Description | Assertion |
|---|-------------|-------------|-----------|
| 1 | `instanceShouldNotBeNull` | Singleton is initialized | `SearchSyntaxResponse.INSTANCE` is not null |
| 2 | `allSectionsShouldBePopulated` | No null sections | All top-level fields are non-null |
| 3 | `stopWordsShouldMatchStopWordsDefault` | Consistency with source of truth | `INSTANCE.stopWords.words` contains exactly the same elements as `StopWords.DEFAULT` |
| 4 | `stopWordsShouldBeSorted` | Alphabetically sorted | Words list is sorted |
| 5 | `stemmingExamplesShouldBeAccurate` | Stemmer produces documented stems | For each example, `Stemmer.stem(example.input)` equals `example.stemmed` |

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Stemming examples become inaccurate if `Stemmer` logic changes | Low | Medium | Unit test verifies each documented example against actual `Stemmer.stem()` output |
| Scoring weights become stale if config defaults change | Low | Medium | Document that weights shown are defaults; alternatively inject `SearchConfig` / `KeywordScoringConfig` and read live values |
| Response grows large over time as more syntax is documented | Low | Low | Response is ~3KB — negligible; AI agents cache it |
| New search features added without updating syntax docs | Medium | Medium | Add a checklist item to feature template: "Update search syntax documentation if search behavior changes" |
| Stop words list changes without response updating | Very Low | Low | Response reads from `StopWords.DEFAULT` at class-load time — always consistent |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `SearchSyntaxResponse` DTO with nested classes and static builder | 1.5 |
| Create `SearchSyntaxResource` endpoint with OpenAPI annotations | 0.5 |
| Integration tests (10 scenarios) | 1.5 |
| Unit tests (5 scenarios) | 0.75 |
| Run full test suite and fix regressions | 0.25 |
| **Total** | **~4.5 hours** |

---

## Files Modified

### New Production Files (2 files)
- `src/main/java/com/fvd/api/resources/SearchSyntaxResource.java` — JAX-RS endpoint for `GET /api/search/syntax`
- `src/main/java/com/fvd/api/dto/SearchSyntaxResponse.java` — Response DTO with nested info classes and static singleton

### Existing Production Files Referenced (not modified)
- `src/main/java/com/fvd/common/StopWords.java` — source of truth for stop words list (read-only)
- `src/main/java/com/fvd/common/Stemmer.java` — used in unit tests to verify stemming examples

### New Test Files (2 files)
- `src/test/java/com/fvd/api/resources/SearchSyntaxResourceTest.java` — integration tests
- `src/test/java/com/fvd/api/dto/SearchSyntaxResponseTest.java` — unit tests

---

END OF FILE
