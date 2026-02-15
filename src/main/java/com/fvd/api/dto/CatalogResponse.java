package com.fvd.api.dto;

import com.fasterxml.jackson.annotation.JsonFilter;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for the catalog endpoint containing subjects, extensions, and versions.
 */
@JsonFilter("fieldSelector")
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class CatalogResponse {

    public List<SubjectInfo> subjects;
    public List<ExtensionInfo> extensions;
    public List<String> versions;

}
