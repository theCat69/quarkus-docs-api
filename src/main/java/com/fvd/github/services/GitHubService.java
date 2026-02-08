package com.fvd.github.services;

import com.fvd.docs.parser.DocParser;
import com.fvd.github.clients.GithubApiClient;
import com.fvd.github.clients.GithubApiFile;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.clients.GithubRepositoryClient;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.InputStream;
import java.util.List;

@Slf4j
@ApplicationScoped
public class GitHubService {

    @RestClient
    GithubApiClient githubApiClient;

    @RestClient
    GithubRepositoryClient githubRepositoryClient;

    @ConfigProperty(name = "app.github.owner")
    String owner;

    @ConfigProperty(name = "app.github.repo")
    String repo;

    private final DocParser docParser;

    public GitHubService(DocParser docParser) {
        this.docParser = docParser;
    }

    public List<GithubApiIndex> fetchIndex(String version) {
        String docsPath = stripTrailingSlash(docParser.docsPrefix());
        return githubApiClient.fetchIndex(owner, repo, docsPath, version);
    }

    public GithubApiFile fetchFileContent(String filePath, String version) {
        return githubApiClient.fetchFile(owner, repo, filePath, version);
    }

    public InputStream fetchZipStream(String version) {
        return githubRepositoryClient.fetchZipStream(owner, repo, version);
    }

    private String stripTrailingSlash(String path) {
        if (path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
