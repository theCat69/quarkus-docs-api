package com.fvd.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fvd.api.dto.meta.ApiInfo;
import com.fvd.api.dto.meta.EndpointMeta;
import com.fvd.api.dto.meta.FiltersMeta;
import com.fvd.api.dto.meta.PaginationMeta;
import com.fvd.api.dto.meta.SearchSyntaxMeta;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top-level response for the API meta/capabilities endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class MetaResponse {

    public ApiInfo apiInfo;
    public List<EndpointMeta> endpoints;
    public SearchSyntaxMeta searchSyntax;
    public FiltersMeta filters;
    public PaginationMeta pagination;

}
