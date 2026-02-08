package com.fvd.github.clients;

import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.github.exceptions.UpstreamException;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "github-api-client")
@ClientHeaderParam(name = "Authorization", value = "Bearer ${github.token}", required = false)
public interface GithubApiClient {

    @GET
    @Path("{owner}/{repo}/contents/{docsPath}")
    List<GithubApiIndex> fetchIndex(@PathParam("owner") String owner,
                                    @PathParam("repo") String repo,
                                    @PathParam("docsPath") @Encoded String docsPath,
                                    @QueryParam("ref") String version);

    @GET
    @Path("{owner}/{repo}/contents/{filePath}")
    GithubApiFile fetchFile(@PathParam("owner") String owner,
                            @PathParam("repo") String repo,
                            @PathParam("filePath") @Encoded String filePath,
                            @QueryParam("ref") String version);

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
            throw new DocNotFoundException("Github api document not found " + response.getStatus());
        }
        throw new UpstreamException("GitHub API returned status " + response.getStatus());
    }
}
