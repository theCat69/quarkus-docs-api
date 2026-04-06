# Pattern: Response DTO with Lombok, JsonFilter, and OpenAPI @Schema annotations
# Demonstrates: @Builder, @NoArgsConstructor, @AllArgsConstructor, @JsonFilter("fieldSelector"),
# @RegisterForReflection, @JsonInclude(NON_NULL), @Schema on fields, and public fields
# for Jackson serialization (used throughout the api.dto package).

```java
package com.fvd.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.quarkus.runtime.annotations.RegisterForReflection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Single chunk result from a documentation search.
 *
 * - @JsonFilter("fieldSelector"): supports dynamic ?fields=id,title query param
 *   (a default serializeAll filter is registered at startup so all fields appear
 *   when ?fields is omitted).
 * - @RegisterForReflection: required for Quarkus native build compatibility.
 * - @JsonInclude(NON_NULL): null fields are omitted from JSON responses.
 * - Public fields: intentional for Jackson deserialization without a separate
 *   @JsonCreator or getters.
 * - @Builder: for fluent construction in services.
 * - @NoArgsConstructor + @AllArgsConstructor: required for Jackson + @Builder co-existence.
 */
@JsonFilter("fieldSelector")
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
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
```
