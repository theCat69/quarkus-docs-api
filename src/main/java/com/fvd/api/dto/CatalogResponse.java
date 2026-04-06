package com.fvd.api.dto;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for the catalog endpoint containing subjects, extensions, and versions.
 */
@JsonFilter("fieldSelector")
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class CatalogResponse {

    public List<SubjectInfo> subjects;
    public List<ExtensionInfo> extensions;
    public List<String> versions;

}
