package com.fvd.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Information about a Quarkus extension.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionInfo {

    public String name;
    public String displayName;
    public String description;
    public int docCount;
    public List<String> keywords;

    /**
     * Backward-compatible constructor without keywords.
     */
    public ExtensionInfo(String name, String displayName, String description, int docCount) {
        this(name, displayName, description, docCount, List.of());
    }

}
