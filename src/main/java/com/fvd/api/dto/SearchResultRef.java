package com.fvd.api.dto;

import com.fasterxml.jackson.annotation.JsonFilter;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Lightweight search result reference for quick discovery.
 */
@JsonFilter("fieldSelector")
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultRef {

    public String path;
    public String title;
    public String subject;
    public String extension;
    public double score;
    public List<String> matchedKeywords;
    public String snippet;

}
