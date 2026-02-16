package com.fvd.search.services;

import com.fvd.common.utils.UrlBuilder;
import com.fvd.indexs.model.ChunkSearchRow;
import com.fvd.indexs.stores.DocChunkStore;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DocChunkSearchService {

    private final DocChunkStore docChunkStore;
    private final UrlBuilder urlBuilder;

    /**
     * Searches doc chunks by query with full-text search, falling back to fuzzy search
     * when no exact results are found at offset 0.
     *
     * @param query     the search query string
     * @param version   the documentation version
     * @param extension optional extension filter (may be null)
     * @param limit     maximum number of results
     * @param offset    pagination offset
     * @return paginated chunk search results
     */
    public PaginatedChunkResult search(String query, String version, String extension, int limit, int offset) {
        log.debug("Searching chunks: query='{}', version='{}', extension='{}', limit={}, offset={}",
                query, version, extension, limit, offset);

        List<ChunkSearchRow> rows = docChunkStore.search(query, version, extension, limit, offset);

        if (rows.isEmpty() && offset == 0) {
            log.debug("No exact results found, falling back to fuzzy search for query='{}'", query);
            rows = docChunkStore.fuzzySearch(query, version, limit);
        }

        List<ChunkSearchResult> results = rows.stream()
                .map(this::toChunkSearchResult)
                .toList();

        return new PaginatedChunkResult(results, results.size(), limit, offset);
    }

    private ChunkSearchResult toChunkSearchResult(ChunkSearchRow row) {
        String url = row.url();
        if (url == null) {
            url = urlBuilder.buildUrl(row.page(), row.section());
        }
        return new ChunkSearchResult(
                row.id(),
                row.page(),
                row.title(),
                row.section(),
                row.summary(),
                row.extensions(),
                row.topics(),
                row.score(),
                url
        );
    }
}
