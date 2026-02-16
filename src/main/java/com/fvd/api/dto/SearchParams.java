package com.fvd.api.dto;

import com.fvd.common.SearchConstants;
import com.fvd.common.validators.InputValidator;
import lombok.Builder;

/**
 * Validated and normalized search parameters built from raw query params.
 */
@Builder
public record SearchParams(
        String version,
        String q,
        String extension,
        int limit,
        int offset
) {

    public static SearchParams fromRaw(
            String version, String q,
            String extension, Integer limit, Integer offset) {
        InputValidator.requireNonEmpty(q, "q");
        InputValidator.validateQueryLength(q);
        return SearchParams.builder()
                .version(InputValidator.resolveVersion(version))
                .q(q)
                .extension(InputValidator.normalizeAndValidateFilter(extension, "extension"))
                .limit(InputValidator.validateLimit(limit, SearchConstants.DEFAULT_LIMIT, SearchConstants.MAX_LIMIT))
                .offset(InputValidator.validateOffset(offset))
                .build();
    }
}
