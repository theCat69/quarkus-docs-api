package com.fvd.api.dto.meta;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Pagination defaults and constraints.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class PaginationMeta {

    public int defaultLimit;
    public int maxLimit;
    public int defaultOffset;

}
