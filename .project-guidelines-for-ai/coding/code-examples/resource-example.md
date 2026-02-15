# Resource Example

Reference for creating JAX-RS REST endpoint classes in this project.

**Key patterns:**
- `@Path` at class level for the base path.
- `@Produces(MediaType.APPLICATION_JSON)` at class level.
- `@RequiredArgsConstructor` for constructor-based dependency injection.
- `@GET` / `@POST` with `@Path` at method level for specific endpoints.
- `@QueryParam` for all request parameters.
- `@Parameter` (MicroProfile OpenAPI) with `description`, `required`, and `example` on every parameter.
- `@Schema(defaultValue = ...)` to document defaults (e.g., version defaults to `main`).
- Use `InputValidator` to validate and resolve inputs at the top of each method.
- Return typed response DTOs (e.g., `SearchResponse<T>`).

**Source:** `com.fvd.search.resources.SearchResource`

```java
package com.fvd.search.resources;

import com.fvd.common.validators.InputValidator;
import com.fvd.search.services.FileSearchResult;
import com.fvd.search.services.SearchService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import java.util.Arrays;
import java.util.List;

@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class SearchResource {
    private final SearchService searchService;

    @GET
    @Path("/files")
    public SearchResponse<FileSearchResult> searchFiles(
            @Parameter(description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
                    required = false, example = "main", schema = @Schema(defaultValue = "main"))
            @QueryParam("version") String version,
            @Parameter(description = "Comma-separated list of search keywords", required = true, example = "security,oidc")
            @QueryParam("keywords") String keywords,
            @Parameter(description = "Optional extension name filter", required = false, example = "quarkus-core")
            @QueryParam("extension") String extension) {
        version = InputValidator.resolveVersion(version);
        InputValidator.validateKeywords(keywords);
        List<String> keywordList = Arrays.asList(keywords.split(","));
        List<FileSearchResult> results = searchService.searchFiles(version, keywordList);
        return new SearchResponse<>(results);
    }
}
```
