package com.fvd.api.dto.meta;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Metadata describing a single API endpoint.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class EndpointMeta {

    public String method;
    public String path;
    public String summary;
    public String description;
    public List<ParameterMeta> parameters;

}
