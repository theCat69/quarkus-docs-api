package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Represents a section within a document.
 */
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
