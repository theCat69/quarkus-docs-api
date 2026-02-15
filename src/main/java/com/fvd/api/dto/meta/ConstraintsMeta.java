package com.fvd.api.dto.meta;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Parameter validation constraints.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class ConstraintsMeta {

    public Integer min;
    public Integer max;
    public String pattern;
    public List<String> allowedValues;

}
