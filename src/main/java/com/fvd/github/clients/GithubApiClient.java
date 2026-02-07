package com.fvd.github.clients;

import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.github.exceptions.UpstreamException;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "github-api-client")
//@ClientHeaderParam(name = "Authorization", value = "Bearer ${app.github.token}", required = false)
public interface GithubApiClient {

    @GET
    @Path("docs/src/main/asciidoc")
    List<GithubApiIndex> fetchIndex(@QueryParam("ref") String version);

    @GET
    @Path("{filePath}")
    String fetchFile(@Encoded String filePath, @QueryParam("ref") String version);

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
            throw new DocNotFoundException("Github api document not found " + response.getStatus());
        }
        throw new UpstreamException("GitHub API returned status " + response.getStatus());
    }
}
