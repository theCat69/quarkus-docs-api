package com.fvd.repository.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Generic search result wrapper with pagination support.
 *
 * @param items the list of matching items for the current page
 * @param total the total number of matches before pagination
 * @param <T> the type of items in the result
 */
@RegisterForReflection
public record SearchResult<T>(
        List<T> items,
        int total
) {
    /**
     * Creates an empty search result.
     */
    public static <T> SearchResult<T> empty() {
        return new SearchResult<>(List.of(), 0);
    }
}
