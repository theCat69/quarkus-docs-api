package com.fvd.indexs.resources;

import com.fvd.common.validators.InputValidator;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.indexs.services.IndexService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Path("/api/index")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class IndexResource {
    private final IndexService indexService;

    @GET
    public List<GithubApiIndex> getIndex(@QueryParam("version") String version) {
        InputValidator.validateVersion(version);
        return indexService.getOrFetchIndex(version);
    }
}
