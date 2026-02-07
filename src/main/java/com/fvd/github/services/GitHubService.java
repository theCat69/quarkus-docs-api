package com.fvd.github.services;

import com.fvd.github.clients.GithubApiClient;
import com.fvd.github.clients.GithubApiFile;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.clients.GithubRepositoryClient;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.InputStream;
import java.util.List;

@ApplicationScoped
public class GitHubService {

    @RestClient
    GithubApiClient githubApiClient;
    @RestClient
    GithubRepositoryClient githubRepositoryClient;

    public List<GithubApiIndex> fetchIndex(String version) {
        return githubApiClient.fetchIndex(version);
    }

    public GithubApiFile fetchFileContent(String filePath, String version) {
        return githubApiClient.fetchFile(filePath, version);
    }

    public InputStream fetchZipStream(String version) {
        return githubRepositoryClient.fetchZipStream(version);
    }
}
