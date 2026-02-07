package com.fvd.github.clients;

import com.fvd.github.exceptions.UpstreamException;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.io.InputStream;

@RegisterRestClient(configKey = "github-repository-client")
@ClientHeaderParam(name = "Authorization", value = "Bearer ${app.github.token}", required = false)
public interface GithubRepositoryClient {

    @GET
    @Path("quarkus/archive/refs/heads/{version}.zip")
    InputStream fetchZipStream(String version);

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        throw new UpstreamException("GitHub repository returned status " + response.getStatus());
    }
}
