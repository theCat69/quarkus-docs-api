package com.fvd.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Represents a section within a document.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class SectionInfo {

    public String title;
    public int level;
    public String content;
    public int startLine;
    public int endLine;

}
