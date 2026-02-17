package com.fvd.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import com.fvd.common.StopWords;

/**
 * Static, machine-readable documentation of search query syntax,
 * supported features, scoring behavior, and examples.
 */
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
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

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class TokenizationInfo {
        public String description;
        public String separator;
        public boolean caseSensitive;
        public int minTokenLength;
        public List<String> rules;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class StemmingInfo {
        public String description;
        public String algorithm;
        public List<StemmingExample> examples;
        public List<String> suffixesStripped;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class StemmingExample {
        public String input;
        public String stemmed;
        public List<String> alsoMatches;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class ScoringInfo {
        public String description;
        public List<MatchTypeInfo> matchTypes;
        public List<LocationWeightInfo> locationWeights;
        public MultiKeywordBoostInfo multiKeywordBoost;
        public FrequencyFactorInfo frequencyFactor;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class MatchTypeInfo {
        public String type;
        public String description;
        public double scoreMultiplier;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class LocationWeightInfo {
        public String location;
        public double weight;
        public String description;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class MultiKeywordBoostInfo {
        public double multiplier;
        public String description;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class FrequencyFactorInfo {
        public String formula;
        public String description;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class StopWordsInfo {
        public String description;
        public String behavior;
        public List<String> words;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class FuzzyMatchingInfo {
        public String description;
        public String appliesTo;
        public String notAppliedTo;
        public String algorithm;
        public double defaultThreshold;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class SupportedFeaturesInfo {
        public List<SupportedFeature> features;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class SupportedFeature {
        public String feature;
        public String description;
        public String example;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class UnsupportedFeaturesInfo {
        public String description;
        public List<UnsupportedFeature> features;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class UnsupportedFeature {
        public String syntax;
        public String description;
        public String workaround;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class FilterInfo {
        public String parameter;
        public String description;
        @JsonProperty("default")
        public String defaultValue;
        public String example;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class QueryExample {
        public String query;
        public String description;
    }

    private static SearchSyntaxResponse buildInstance() {
        SearchSyntaxResponse response = new SearchSyntaxResponse();

        response.tokenization = new TokenizationInfo(
                "Queries are processed by PostgreSQL plainto_tsquery which tokenizes, stems, and removes stop words.",
                "whitespace (spaces)",
                false,
                1,
                List.of(
                        "Input is passed to plainto_tsquery('english', query)",
                        "Text is tokenized on whitespace and punctuation",
                        "English stop words are removed",
                        "Remaining tokens are stemmed using the Snowball English stemmer",
                        "Stemmed tokens are combined with AND logic for matching against tsvector indexes"
                )
        );

        response.stemming = new StemmingInfo(
                "PostgreSQL uses the Snowball English stemmer which reduces words to their root forms for broader matching.",
                "Snowball English stemmer (PostgreSQL built-in)",
                List.of(
                        new StemmingExample("security", "secur",
                                List.of("securing", "secured", "securities")),
                        new StemmingExample("configuration", "configur",
                                List.of("configurable", "configured", "configuring")),
                        new StemmingExample("running", "run",
                                List.of("runner", "runs")),
                        new StemmingExample("authentication", "authent",
                                List.of("authenticated", "authenticating")),
                        new StemmingExample("injection", "inject",
                                List.of("injecting", "injectable")),
                        new StemmingExample("management", "manag",
                                List.of("manageable", "managing"))
                ),
                List.of("ation", "tion", "sion", "ment", "ness", "ing", "ed", "ly",
                        "er", "es", "s")
        );

        response.scoring = new ScoringInfo(
                "Results are scored using PostgreSQL ts_rank function which measures relevance " +
                        "based on term frequency and document structure.",
                List.of(
                        new MatchTypeInfo("fts",
                                "Full-text search via plainto_tsquery with ts_rank scoring", 1.0),
                        new MatchTypeInfo("fuzzy",
                                "Trigram similarity via pg_trgm when FTS returns no results", 0.1)
                ),
                List.of(
                        new LocationWeightInfo("content", 1.0,
                                "ts_rank scores based on term frequency in document content")
                ),
                new MultiKeywordBoostInfo(1.0,
                        "Multiple keywords are AND-combined by plainto_tsquery; no additional boost applied"),
                new FrequencyFactorInfo(
                        "ts_rank(content_tsv, plainto_tsquery('english', query))",
                        "PostgreSQL ts_rank computes relevance based on term frequency and proximity")
        );

        response.stopWords = new StopWordsInfo(
                "PostgreSQL english dictionary automatically removes stop words during tsquery parsing.",
                "Silently removed by PostgreSQL during query parsing. " +
                        "A query containing only stop words may return no results.",
                StopWords.DEFAULT.stream().sorted().toList()
        );

        response.fuzzyMatching = new FuzzyMatchingInfo(
                "When full-text search returns no results, a fallback fuzzy search uses " +
                        "PostgreSQL pg_trgm trigram similarity to find approximate matches.",
                "Fallback when FTS returns no results at offset 0",
                "Primary search (uses full-text search with plainto_tsquery)",
                "pg_trgm trigram similarity (similarity(content, query) > 0.1)",
                0.1
        );

        response.supported = new SupportedFeaturesInfo(List.of(
                new SupportedFeature("PostgreSQL Full-Text Search",
                        "Queries processed by plainto_tsquery with English stemming and stop word removal",
                        "security oidc"),
                new SupportedFeature("Snowball Stemming",
                        "English Snowball stemmer reduces words to root forms for broader matching",
                        "configure"),
                new SupportedFeature("Trigram Fuzzy Fallback",
                        "pg_trgm similarity search when FTS returns no results",
                        "securty"),
                new SupportedFeature("Extension filter",
                        "Filter results by Quarkus extension name",
                        "q=config&extension=quarkus-core"),
                new SupportedFeature("Pagination",
                        "Use limit and offset parameters to paginate results",
                        "q=security&limit=10&offset=20")
        ));

        response.unsupported = new UnsupportedFeaturesInfo(
                "The following query syntax patterns are NOT supported and will be treated as literal keyword text.",
                List.of(
                        new UnsupportedFeature("\"quoted phrases\"",
                                "Quotes are treated as literal characters, not phrase delimiters. " +
                                        "plainto_tsquery does not support phrase matching.",
                                "Use individual keywords: 'rest endpoint' instead of '\"rest endpoint\"'"),
                        new UnsupportedFeature("AND / OR / NOT",
                                "plainto_tsquery treats all words as AND-combined. " +
                                        "OR and NOT operators are not supported.",
                                "Use space-separated keywords for AND-like behavior. " +
                                        "OR/NOT are not supported."),
                        new UnsupportedFeature("* or ? wildcards",
                                "Glob/wildcard patterns are not supported. " +
                                        "Characters are treated as literals.",
                                "Rely on stemming for broader matches."),
                        new UnsupportedFeature("field:value",
                                "Field-specific search (e.g., 'title:security') is not supported.",
                                "Use the 'subject' or 'extension' query parameters for filtering."),
                        new UnsupportedFeature("+required -excluded",
                                "Required/excluded term modifiers are not supported.",
                                "All keywords are implicitly AND-combined. " +
                                        "Use subject/extension filters to narrow results."),
                        new UnsupportedFeature("~ fuzzy operator",
                                "Tilde-based fuzzy search syntax is not supported.",
                                "Fuzzy matching via pg_trgm is applied automatically as a fallback " +
                                        "when FTS returns no results.")
                )
        );

        response.filters = List.of(
                new FilterInfo("version", "Quarkus version branch or tag", "main", "3.17"),
                new FilterInfo("subject", "Documentation subject category filter", null, "security"),
                new FilterInfo("extension", "Quarkus extension name filter", null, "quarkus-resteasy-reactive"),
                new FilterInfo("limit", "Maximum number of results to return", "20", "10"),
                new FilterInfo("offset", "Number of results to skip for pagination", "0", "20")
        );

        response.examples = List.of(
                new QueryExample("security oidc",
                        "Search for documents about security and OIDC. Both words are stemmed and " +
                                "AND-combined by PostgreSQL."),
                new QueryExample("rest endpoint",
                        "Search for REST endpoint documentation. Both terms must match in the content."),
                new QueryExample("configure datasource",
                        "'configure' is stemmed by Snowball to match 'configuration', 'configuring', etc."),
                new QueryExample("hibernate orm",
                        "Search for Hibernate ORM docs. " +
                                "Use extension=quarkus-hibernate-orm for precise results."),
                new QueryExample("grpc",
                        "Single keyword search. Stemmed and matched against tsvector index.")
        );

        response.tips = List.of(
                "Use specific, meaningful keywords — avoid generic terms like 'how', 'what', 'use'",
                "PostgreSQL stemming handles word variants automatically (e.g., 'configuring' matches 'configuration')",
                "Combine 2-3 keywords for best results — all terms are AND-combined",
                "Use the 'extension' parameter to filter results by Quarkus extension",
                "If FTS returns no results, the API automatically falls back to fuzzy trigram matching",
                "Do not use quotes, boolean operators, or wildcard characters — they are treated as literal text",
                "If your query returns no results, try fewer or broader keywords",
                "Stop words (a, the, is, how, etc.) are automatically removed by PostgreSQL"
        );

        return response;
    }
}
