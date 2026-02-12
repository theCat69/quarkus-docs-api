package com.fvd.api.dto;

import com.fvd.common.SearchConstants;
import com.fvd.common.validators.InputValidator;
import lombok.Builder;

import java.util.List;

/**
 * Validated and normalized search parameters built from raw query params.
 */
@Builder
public record SearchParams(
        String version,
        List<String> keywords,
        String subject,
        String extension,
        int limit,
        int offset
) {

    public static SearchParams fromRaw(
            String version, String keywords, String subject,
            String extension, Integer limit, Integer offset) {
        return SearchParams.builder()
                .version(InputValidator.resolveVersion(version))
                .keywords(InputValidator.parseKeywords(keywords))
                .subject(normalizeFilter(subject))
                .extension(normalizeFilter(extension))
                .limit(InputValidator.validateLimit(limit, SearchConstants.DEFAULT_LIMIT, SearchConstants.MAX_LIMIT))
                .offset(InputValidator.validateOffset(offset))
                .build();
    }

    private static String normalizeFilter(String filter) {
        return (filter == null || filter.isBlank()) ? null : filter.trim();
    }
}
