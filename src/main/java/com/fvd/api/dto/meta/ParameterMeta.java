package com.fvd.api.dto.meta;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Metadata describing a single endpoint parameter.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class ParameterMeta {

    public String name;
    public String type;
    public boolean required;
    public String defaultValue;
    public String description;
    public ConstraintsMeta constraints;

}
