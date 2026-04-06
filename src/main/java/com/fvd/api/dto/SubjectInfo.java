package com.fvd.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Information about a documentation subject category.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class SubjectInfo {

    public String name;
    public String displayName;
    public String description;
    public int docCount;
    public List<String> keywords;

}
