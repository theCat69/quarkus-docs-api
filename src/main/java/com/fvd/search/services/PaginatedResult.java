package com.fvd.search.services;

import java.util.List;

/**
 * Holds a page of results along with the total count before pagination.
 */
public record PaginatedResult<T>(List<T> items, int total) {
}
