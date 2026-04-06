package com.fvd.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Represents a code block within a document.
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class CodeBlockInfo {

    public String language;
    public String content;
    public String context;
    public int startLine;
    public int endLine;

}
