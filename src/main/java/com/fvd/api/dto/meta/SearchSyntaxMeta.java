package com.fvd.api.dto.meta;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Summary of search syntax capabilities with a link to the detailed endpoint.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class SearchSyntaxMeta {

    public String keywordSeparator;
    public String detailedSyntaxEndpoint;
    public List<String> supportedFeatures;
    public List<String> unsupportedFeatures;
    public List<String> tips;

}
