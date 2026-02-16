package com.fvd.indexs.model;

import java.util.List;

public record ChunkSearchRow(
        String id,
        String version,
        String page,
        String title,
        String section,
        String url,
        List<String> topics,
        List<String> extensions,
        String summary,
        String content,
        double score
) {
}
