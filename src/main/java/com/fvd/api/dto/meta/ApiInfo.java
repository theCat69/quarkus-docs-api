package com.fvd.api.dto.meta;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * API identification and default configuration.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class ApiInfo {

    public String name;
    public String description;
    public String defaultVersion;

}
