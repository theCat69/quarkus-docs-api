package com.fvd.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fvd.common.StopWords;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

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
                "Keywords are split by whitespace. Each token is lowercased and stemmed independently.",
                "whitespace (spaces, tabs)",
                false,
                3,
                List.of(
                        "Input is split on whitespace into individual tokens",
                        "Each token is converted to lowercase",
                        "Stop words are removed before processing",
                        "Each remaining token is stemmed using suffix-stripping rules",
                        "Stemmed tokens are matched against the keyword index"
                )
        );

        response.stemming = new StemmingInfo(
                "A simple English suffix-stripping stemmer groups related word forms. " +
                        "The stemmer is deterministic and consistent, not linguistically perfect.",
                "Custom suffix-stripping (Porter-like)",
                List.of(
                        new StemmingExample("security", "secur",
                                List.of("securing", "secured")),
                        new StemmingExample("configuration", "configur",
                                List.of("configurable", "configured", "configuring")),
                        new StemmingExample("running", "run",
                                List.of("runner", "runs")),
                        new StemmingExample("authentication", "authentic",
                                List.of("authenticity")),
                        new StemmingExample("injection", "injec",
                                List.of("injecting", "injectable")),
                        new StemmingExample("management", "manage",
                                List.of("manageable"))
                ),
                List.of("ation", "tion", "sion", "ment", "ness", "able", "ible",
                        "ous", "ive", "ity", "ful", "less", "ing", "ed", "ly",
                        "er", "est", "es", "s")
        );

        response.scoring = new ScoringInfo(
                "Documents are scored based on keyword match location, match type, and query structure.",
                List.of(
                        new MatchTypeInfo("exact",
                                "Stemmed query exactly matches indexed keyword", 1.0),
                        new MatchTypeInfo("prefix",
                                "Indexed keyword starts with stemmed query", 0.8)
                ),
                List.of(
                        new LocationWeightInfo("filename", 10.0,
                                "Keyword appears in the document filename"),
                        new LocationWeightInfo("title", 8.0,
                                "Keyword appears in the document title (H1)"),
                        new LocationWeightInfo("section", 5.0,
                                "Keyword appears in a section heading (H2)"),
                        new LocationWeightInfo("subtitle", 2.0,
                                "Keyword appears in a subtitle (H3+)"),
                        new LocationWeightInfo("body", 1.0,
                                "Keyword appears in body text")
                ),
                new MultiKeywordBoostInfo(1.5,
                        "Queries with 2+ keywords receive a 1.5x score boost when multiple keywords match"),
                new FrequencyFactorInfo("min(1.0 + log(count), 2.0)",
                        "Repeated occurrences of a keyword increase score logarithmically, capped at 2.0x")
        );

        response.stopWords = new StopWordsInfo(
                "Stop words are common words automatically removed from queries before searching. " +
                        "A query containing only stop words returns HTTP 400.",
                "Silently removed from query. If all keywords are stop words, " +
                        "the API returns 400 Bad Request.",
                StopWords.DEFAULT.stream().sorted().toList()
        );

        response.fuzzyMatching = new FuzzyMatchingInfo(
                "Fuzzy matching is used only for section title lookups (not for general keyword search). " +
                        "It combines Levenshtein similarity, substring containment, and word overlap " +
                        "to find the best matching section title.",
                "Section title search only (GET /api/documents with sectionTitle parameter)",
                "Keyword search (GET /api/search, GET /api/documents with keywords parameter)",
                "Weighted combination: Levenshtein (0.4) + Containment (0.35) + Word Overlap (0.25)",
                0.3
        );

        response.supported = new SupportedFeaturesInfo(List.of(
                new SupportedFeature("Space-separated keywords",
                        "Multiple keywords separated by spaces: 'security oidc'",
                        "security oidc"),
                new SupportedFeature("Stemming",
                        "Words are reduced to stems for broader matching: 'configuring' matches 'configuration'",
                        "configure"),
                new SupportedFeature("Prefix matching",
                        "Short query stems match longer indexed keywords at 80% score",
                        "sec"),
                new SupportedFeature("Multi-keyword boost",
                        "Queries with 2+ keywords get 1.5x score boost",
                        "rest security"),
                new SupportedFeature("Subject filter",
                        "Filter results by documentation subject category",
                        "keywords=security&subject=security"),
                new SupportedFeature("Extension filter",
                        "Filter results by Quarkus extension name",
                        "keywords=config&extension=quarkus-core"),
                new SupportedFeature("Pagination",
                        "Use limit and offset parameters to paginate results",
                        "keywords=security&limit=10&offset=20")
        ));

        response.unsupported = new UnsupportedFeaturesInfo(
                "The following query syntax patterns are NOT supported and will be treated as literal keyword text.",
                List.of(
                        new UnsupportedFeature("\"quoted phrases\"",
                                "Quotes are treated as literal characters, not phrase delimiters. " +
                                        "Use space-separated keywords instead.",
                                "Use individual keywords: 'rest endpoint' instead of '\"rest endpoint\"'"),
                        new UnsupportedFeature("AND / OR / NOT",
                                "Boolean operators are treated as regular keywords " +
                                        "(and 'and' is a stop word that gets removed).",
                                "Use space-separated keywords for AND-like behavior. " +
                                        "OR/NOT are not supported."),
                        new UnsupportedFeature("* or ? wildcards",
                                "Glob/wildcard patterns are not supported. " +
                                        "Characters are treated as literals.",
                                "Rely on stemming and prefix matching for broader matches."),
                        new UnsupportedFeature("field:value",
                                "Field-specific search (e.g., 'title:security') is not supported.",
                                "Use the 'subject' or 'extension' query parameters for filtering."),
                        new UnsupportedFeature("+required -excluded",
                                "Required/excluded term modifiers are not supported.",
                                "All keywords are implicitly searched. " +
                                        "Use subject/extension filters to narrow results."),
                        new UnsupportedFeature("~ fuzzy operator",
                                "Tilde-based fuzzy search syntax is not supported for keyword search.",
                                "Stemming provides automatic fuzzy-like matching for word variants.")
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
                        "Search for documents about security and OIDC. Both keywords are stemmed and searched. " +
                                "Multi-keyword boost applies."),
                new QueryExample("rest endpoint",
                        "Search for REST endpoint documentation. 'rest' and 'endpoint' are searched independently."),
                new QueryExample("configure datasource",
                        "'configure' is stemmed to 'configur', matching 'configuration', 'configurable', etc. " +
                                "'datasource' is searched as-is."),
                new QueryExample("hibernate orm",
                        "Search for Hibernate ORM documentation. " +
                                "Use with extension=quarkus-hibernate-orm for more precise results."),
                new QueryExample("grpc",
                        "Single keyword search. No multi-keyword boost, " +
                                "but still benefits from stemming and prefix matching.")
        );

        response.tips = List.of(
                "Use specific, meaningful keywords — avoid generic terms like 'how', 'what', 'use'",
                "Prefer root word forms: 'config' instead of 'configuration' " +
                        "(stemming helps, but shorter roots improve prefix matching)",
                "Combine 2-3 keywords for best results — multi-keyword queries get a 1.5x score boost",
                "Use the 'subject' parameter to narrow results to a specific documentation category",
                "Use the 'extension' parameter to filter results by Quarkus extension",
                "Do not use quotes, boolean operators, or wildcard characters — " +
                        "they are treated as literal text",
                "If your query returns no results, try fewer or broader keywords",
                "Stop words (a, the, is, how, etc.) are automatically removed — no need to include them"
        );

        return response;
    }
}
