package com.fvd.github.clients;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.InputStream;

@ApplicationScoped
public class GitHubService {

    @RestClient
    GithubApiClient githubApiClient;
    @RestClient
    GithubRepositoryClient githubRepositoryClient;

    public String fetchIndex(String version) {
        return githubApiClient.fetchIndex(version);
    }

    public String fetchFileContent(String filePath, String version) {
        return githubApiClient.fetchFile(filePath, version);
    }

    public InputStream fetchZipStream(String version) {
        return githubRepositoryClient.fetchZipStream(version);
    }
}
