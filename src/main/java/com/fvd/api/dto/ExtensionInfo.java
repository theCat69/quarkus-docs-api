package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Information about a Quarkus extension.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionInfo {

    public String name;
    public String displayName;
    public String description;
    public int docCount;

}
