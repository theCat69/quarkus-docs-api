package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Represents a code block within a document.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class CodeBlockInfo {

    public String language;
    public String content;
    public String context;
    public int startLine;
    public int endLine;

}
