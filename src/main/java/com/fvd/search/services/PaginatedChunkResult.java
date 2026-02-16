package com.fvd.search.services;

import java.util.List;

/**
 * Paginated wrapper for chunk search results.
 */
public record PaginatedChunkResult(
        List<ChunkSearchResult> results,
        int total,
        int limit,
        int offset
) {
}
