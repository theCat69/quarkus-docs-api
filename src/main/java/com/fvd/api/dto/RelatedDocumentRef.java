package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Lightweight reference to a related document with similarity score.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class RelatedDocumentRef {

    public String path;
    public String title;
    public String description;
    public String subject;
    public String extension;
    public double similarityScore;
    public List<String> sharedKeywords;

}
