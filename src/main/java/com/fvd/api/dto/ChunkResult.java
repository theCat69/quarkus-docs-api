package com.fvd.api.dto;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Single chunk result from a semantic or keyword-based chunk search.
 */
@JsonFilter("fieldSelector")
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkResult {

    @Schema(description = "Unique identifier of the chunk")
    public String id;

    @Schema(description = "Source page path the chunk was extracted from")
    public String page;

    @Schema(description = "Title of the source document")
    public String title;

    @Schema(description = "Section heading the chunk belongs to")
    public String section;

    @Schema(description = "Short summary or text content of the chunk")
    public String summary;

    @Schema(description = "Quarkus extensions related to this chunk")
    public List<String> extensions;

    @Schema(description = "Topics or tags associated with this chunk")
    public List<String> topics;

    @Schema(description = "Relevance score of the chunk for the search query")
    public double score;

    @Schema(description = "URL to the original documentation page")
    public String url;

}
