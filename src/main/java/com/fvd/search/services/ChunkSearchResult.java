package com.fvd.search.services;

import java.util.List;

/**
 * Represents a single chunk search result with metadata and relevance score.
 */
public record ChunkSearchResult(
        String id,
        String page,
        String title,
        String section,
        String summary,
        List<String> extensions,
        List<String> topics,
        double score,
        String url
) {
}
