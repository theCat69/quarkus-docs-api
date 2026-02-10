# Feature 18: Centralize All Scoring Constants into SearchConfig @ConfigMapping

Replace all hardcoded scoring constants (boosts, weights, thresholds) scattered across `KeywordIndexer`, `CodeSampleIndexer`, `SearchService`, `FuzzyMatcher`, and `AsciidocParser` with a single Quarkus `@ConfigMapping(prefix = "search")` interface. All values become configurable via `application.properties` with `@WithDefault` annotations preserving current behavior.

## Scope and behavior

- Create a `SearchConfig` interface in `com.fvd.search` annotated with `@ConfigMapping(prefix = "search")`.
- Use nested sub-interfaces for logical grouping: `Boost`, `Fuzzy`, `Index`, `Snippet`.
- Every constant uses `@WithDefault` with the current hardcoded value, so no behavioral change unless explicitly overridden in config.
- Migrate existing `keywords.file.minimal.score` config property (currently injected via `@ConfigProperty` in `KeywordIndexer`) to `search.index.min-keyword-score`. Remove the old `@ConfigProperty` field from `KeywordIndexer`.
- Remove the old `keywords.file.minimal.score` entries from `application.properties` and `%test` profile; replace with `search.index.min-keyword-score` entries.
- `FuzzyMatcher` is currently a `final class` with a private constructor and all static methods (in `com.fvd.common.matchers`). Convert it to an `@ApplicationScoped` CDI bean so it can receive `SearchConfig` injection. All static methods become instance methods. Update all call sites: `SearchService.getSectionContent()` and any other references to use the injected bean.
- `AsciidocParser` (in `com.fvd.asciidocs.parser`) currently has a hardcoded `MIN_TOKEN_LENGTH = 3`. Inject `SearchConfig` and use `searchConfig.index().minTokenLength()` instead.
- `KeywordIndexer` currently injects `fileEntryKeywordMinimalScore` via `@ConfigProperty`. Replace with `SearchConfig` injection and use `searchConfig.index().minKeywordScore()`.
- `KeywordIndexer` hardcodes `FILENAME_BOOST = 10` and `TITLE_BOOST = 5`. Replace with `searchConfig.boost().filenameBoost()` and `searchConfig.boost().titleBoost()`.
- `CodeSampleIndexer` hardcodes `IMPORT_BOOST = 5`, `FILENAME_BOOST = 10`, `SECTION_TITLE_BOOST = 5`. Replace with corresponding `searchConfig.boost()` methods.
- `SearchService` hardcodes `MULTI_KEYWORD_BOOST = 1.5` and `SNIPPET_CONTEXT = 100`. Replace with `searchConfig.boost().multiKeywordBoost()` and `searchConfig.snippet().contextSize()`.
- Proactively include `search.boost.prefix-match-multiplier` with default `0.8` for upcoming Feature 20 (prefix matching). This constant is not referenced by any current code but is pre-defined so Feature 20 can use it without config changes.
- No changes to REST endpoints, response shapes, indexing pipeline, or SQLite schema.
- No behavioral changes — all defaults match current hardcoded values exactly.

## Internal interfaces

- `SearchConfig` (`@ConfigMapping(prefix = "search")`) with nested sub-interfaces:
  - `Boost boost()` — scoring multipliers and boost values.
  - `Fuzzy fuzzy()` — fuzzy matching weights and thresholds.
  - `Index index()` — indexing parameters.
  - `Snippet snippet()` — snippet generation parameters.

### Complete SearchConfig interface structure

```java
package com.fvd.search;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "search")
public interface SearchConfig {

    Boost boost();
    Fuzzy fuzzy();
    Index index();
    Snippet snippet();

    interface Boost {
        @WithDefault("10")
        int filenameBoost();

        @WithDefault("5")
        int titleBoost();

        @WithDefault("5")
        int importBoost();

        @WithDefault("5")
        int sectionTitleBoost();

        @WithDefault("1.5")
        double multiKeywordBoost();

        @WithDefault("0.8")
        double prefixMatchMultiplier();
    }

    interface Fuzzy {
        @WithDefault("0.4")
        double levenshteinWeight();

        @WithDefault("0.35")
        double containmentWeight();

        @WithDefault("0.25")
        double wordOverlapWeight();

        @WithDefault("0.3")
        double defaultThreshold();

        @WithDefault("0.5")
        double containmentPartialThreshold();

        @WithDefault("0.3")
        double wordOverlapKeywordThreshold();
    }

    interface Index {
        @WithDefault("2")
        int minKeywordScore();

        @WithDefault("3")
        int minTokenLength();
    }

    interface Snippet {
        @WithDefault("100")
        int contextSize();
    }
}
```

### Property key mapping

| Property key | Default | Source constant | Current location |
|---|---|---|---|
| `search.boost.filename-boost` | `10` | `FILENAME_BOOST` | `KeywordIndexer`, `CodeSampleIndexer` |
| `search.boost.title-boost` | `5` | `TITLE_BOOST` | `KeywordIndexer` |
| `search.boost.import-boost` | `5` | `IMPORT_BOOST` | `CodeSampleIndexer` |
| `search.boost.section-title-boost` | `5` | `SECTION_TITLE_BOOST` | `CodeSampleIndexer` |
| `search.boost.multi-keyword-boost` | `1.5` | `MULTI_KEYWORD_BOOST` | `SearchService` |
| `search.boost.prefix-match-multiplier` | `0.8` | *(new for Feature 20)* | — |
| `search.fuzzy.levenshtein-weight` | `0.4` | `0.4` literal | `FuzzyMatcher.combinedScore()` |
| `search.fuzzy.containment-weight` | `0.35` | `0.35` literal | `FuzzyMatcher.combinedScore()` |
| `search.fuzzy.word-overlap-weight` | `0.25` | `0.25` literal | `FuzzyMatcher.combinedScore()` |
| `search.fuzzy.default-threshold` | `0.3` | `DEFAULT_THRESHOLD` | `FuzzyMatcher.bestMatch()` |
| `search.fuzzy.containment-partial-threshold` | `0.5` | `0.5` literal | `FuzzyMatcher.determineMatchType()` |
| `search.fuzzy.word-overlap-keyword-threshold` | `0.3` | `0.3` literal | `FuzzyMatcher.determineMatchType()` |
| `search.index.min-keyword-score` | `2` | `fileEntryKeywordMinimalScore` | `KeywordIndexer` (via `@ConfigProperty`) |
| `search.index.min-token-length` | `3` | `MIN_TOKEN_LENGTH` | `AsciidocParser` |
| `search.snippet.context-size` | `100` | `SNIPPET_CONTEXT` | `SearchService` |

## Files to create

- `src/main/java/com/fvd/search/SearchConfig.java` — the `@ConfigMapping` interface with nested sub-interfaces as shown above.
- `src/test/java/com/fvd/search/TestSearchConfig.java` — a test helper that implements `SearchConfig` and all nested interfaces with hardcoded defaults, for use in unit tests that construct services manually (e.g., `KeywordIndexerTest`, `CodeSampleIndexerTest`, `SearchServiceTest`, `FuzzyMatcherTest`, `AsciidocParserTest`). This avoids requiring CDI in pure unit tests.

## Files to modify

- `src/main/java/com/fvd/indexs/indexers/KeywordIndexer.java` — inject `SearchConfig`; replace `FILENAME_BOOST`, `TITLE_BOOST` constants and `fileEntryKeywordMinimalScore` field with config lookups.
- `src/main/java/com/fvd/indexs/indexers/CodeSampleIndexer.java` — inject `SearchConfig`; replace `IMPORT_BOOST`, `FILENAME_BOOST`, `SECTION_TITLE_BOOST` constants with config lookups.
- `src/main/java/com/fvd/search/services/SearchService.java` — inject `SearchConfig`; replace `MULTI_KEYWORD_BOOST` and `SNIPPET_CONTEXT` constants with config lookups. Update `FuzzyMatcher` usage from static calls to injected bean calls.
- `src/main/java/com/fvd/common/matchers/FuzzyMatcher.java` — convert from `final class` with static methods to `@ApplicationScoped` CDI bean with instance methods. Inject `SearchConfig`; replace hardcoded weights (`0.4`, `0.35`, `0.25`), `DEFAULT_THRESHOLD` (`0.3`), and `determineMatchType` thresholds (`0.5`, `0.3`) with config lookups.
- `src/main/java/com/fvd/asciidocs/parser/AsciidocParser.java` — inject `SearchConfig`; replace `MIN_TOKEN_LENGTH = 3` constant with `searchConfig.index().minTokenLength()`.
- `src/main/resources/application.properties` — remove `keywords.file.minimal.score=2` and `%test.keywords.file.minimal.score=1`; add `search.index.min-keyword-score=2` and `%test.search.index.min-keyword-score=1`. No other property entries needed (all other values use `@WithDefault`).
- `src/test/java/com/fvd/indexs/indexers/KeywordIndexerTest.java` — update `setUp()` to pass `TestSearchConfig` instance to `KeywordIndexer` constructor; remove direct `fileEntryKeywordMinimalScore` field assignment.
- `src/test/java/com/fvd/indexs/indexers/CodeSampleIndexerTest.java` — update `setUp()` to pass `TestSearchConfig` instance to `CodeSampleIndexer` constructor.
- `src/test/java/com/fvd/search/services/SearchServiceTest.java` — update `setUp()` to pass `TestSearchConfig` instance to `SearchService` constructor; update `FuzzyMatcher` from static usage to injected bean.
- `src/test/java/com/fvd/common/matchers/FuzzyMatcherTest.java` — update to construct `FuzzyMatcher` as a bean with `TestSearchConfig`; update all method calls from static to instance.
- `src/test/java/com/fvd/asciidocs/parser/AsciidocParserTest.java` — update to pass `TestSearchConfig` instance to `AsciidocParser` constructor.

## Tasks

- [ ] Create `SearchConfig` interface in `com.fvd.search` with `@ConfigMapping(prefix = "search")` and nested `Boost`, `Fuzzy`, `Index`, `Snippet` sub-interfaces, each with `@WithDefault` annotations matching current hardcoded values.
- [ ] Create `TestSearchConfig` helper class in `src/test/java/com/fvd/search/` implementing `SearchConfig` and all nested interfaces with default values matching `@WithDefault` annotations.
- [ ] Add unit tests verifying `TestSearchConfig` returns correct defaults for all properties.
- [ ] Refactor `KeywordIndexer`: inject `SearchConfig`; replace `FILENAME_BOOST`, `TITLE_BOOST`, `fileEntryKeywordMinimalScore` with config lookups; update constructor.
- [ ] Update `KeywordIndexerTest`: pass `TestSearchConfig` to `KeywordIndexer`; remove `indexer.fileEntryKeywordMinimalScore = 2` direct assignment.
- [ ] Refactor `CodeSampleIndexer`: inject `SearchConfig`; replace `IMPORT_BOOST`, `FILENAME_BOOST`, `SECTION_TITLE_BOOST` with config lookups; update constructor.
- [ ] Update `CodeSampleIndexerTest`: pass `TestSearchConfig` to `CodeSampleIndexer`.
- [ ] Refactor `SearchService`: inject `SearchConfig`; replace `MULTI_KEYWORD_BOOST` and `SNIPPET_CONTEXT` with config lookups; change `getScores()` from static to instance method; update `FuzzyMatcher` usage to injected bean.
- [ ] Update `SearchServiceTest`: pass `TestSearchConfig` and `FuzzyMatcher` instance to `SearchService`.
- [ ] Refactor `FuzzyMatcher`: convert from `final class` with static methods to `@ApplicationScoped` bean; inject `SearchConfig`; replace all hardcoded weights and thresholds with config lookups; remove private constructor.
- [ ] Update `FuzzyMatcherTest`: construct `FuzzyMatcher` with `TestSearchConfig`; change all static method calls to instance method calls.
- [ ] Refactor `AsciidocParser`: inject `SearchConfig`; replace `MIN_TOKEN_LENGTH = 3` with `searchConfig.index().minTokenLength()`.
- [ ] Update `AsciidocParserTest`: pass `TestSearchConfig` to `AsciidocParser`.
- [ ] Update `application.properties`: remove `keywords.file.minimal.score=2` and `%test.keywords.file.minimal.score=1`; add `search.index.min-keyword-score=2` and `%test.search.index.min-keyword-score=1`.
- [ ] Verify all existing tests pass with no behavioral changes (all defaults match prior hardcoded values).
- [ ] Add integration test (`@QuarkusTest`) confirming `SearchConfig` is injectable and returns correct defaults.
- [ ] Add integration test confirming search results are identical before and after the refactor (regression test).

## Acceptance criteria

- All 15 scoring constants are sourced from `SearchConfig` — no hardcoded numeric literals remain in `KeywordIndexer`, `CodeSampleIndexer`, `SearchService`, `FuzzyMatcher`, or `AsciidocParser` for these values.
- `SearchConfig` is a single `@ConfigMapping(prefix = "search")` interface with nested sub-interfaces.
- All `@WithDefault` values match the previously hardcoded constants exactly.
- `keywords.file.minimal.score` is fully replaced by `search.index.min-keyword-score` in config and code.
- `FuzzyMatcher` is an `@ApplicationScoped` CDI bean with instance methods; no static method calls remain.
- `AsciidocParser` uses `SearchConfig` for `MIN_TOKEN_LENGTH`.
- All existing tests pass without behavioral changes.
- Overriding any property in `application.properties` changes runtime behavior accordingly.
- `TestSearchConfig` provides a non-CDI test helper for unit tests that construct services manually.

## Risks

- **FuzzyMatcher static-to-CDI conversion**: Converting `FuzzyMatcher` from static utility to CDI bean is the most invasive change. All call sites (currently `SearchService.getSectionContent()`) must be updated. Any missed call site will fail at compile time (static method on instance reference), so risk is low.
- **Config key migration**: Renaming `keywords.file.minimal.score` to `search.index.min-keyword-score` requires updating `application.properties` in all profiles (main, dev, test). If any profile is missed, the default `@WithDefault("2")` applies, which matches the old default — so the risk of silent breakage is low.
- **Test setup changes**: Every unit test that manually constructs `KeywordIndexer`, `CodeSampleIndexer`, `SearchService`, `FuzzyMatcher`, or `AsciidocParser` must now pass a `SearchConfig` (or `TestSearchConfig`) instance. This is a broad change across test files but is mechanical.
- **`@ConfigMapping` validation**: Quarkus validates `@ConfigMapping` interfaces at build time. Typos in property names or type mismatches will surface during `./gradlew build`, not at runtime.

## Dependencies

- None — this is a foundational refactor with no external feature dependencies.

## Downstream impact

- **Feature 20 (Prefix Matching)**: Can immediately use `searchConfig.boost().prefixMatchMultiplier()` without any config additions.
- **Features 19, 21, 22, 23**: All benefit from centralized config. New constants for these features can be added as additional methods on the existing `SearchConfig` sub-interfaces.
