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

    @ConfigProperty(name = "app.github.branch", defaultValue = "main")
    String branch;

    private final DocParser docParser;

    public GitHubService(DocParser docParser) {
        this.docParser = docParser;
    }

    public List<GithubApiIndex> fetchIndex(String version) {
        String docsPath = stripTrailingSlash(docParser.docsPrefix(version));
        return githubApiClient.fetchIndex(owner, repo, docsPath, branch);
    }

    public GithubApiFile fetchFileContent(String filePath, String version) {
        return githubApiClient.fetchFile(owner, repo, filePath, branch);
    }

    public InputStream fetchZipStream() {
        return githubRepositoryClient.fetchZipStream(owner, repo, branch);
    }

    public InputStream fetchZipStreamForRepo(String owner, String repo, String branch) {
        return githubRepositoryClient.fetchZipStream(owner, repo, branch);
    }

    public List<GithubApiIndex> fetchIndexForRepo(String owner, String repo, String docsPath, String branch) {
        return githubApiClient.fetchIndex(owner, repo, docsPath, branch);
    }

    public GithubApiFile fetchFileContentForRepo(String owner, String repo, String filePath, String branch) {
        return githubApiClient.fetchFile(owner, repo, filePath, branch);
    }

    private String stripTrailingSlash(String path) {
        if (path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
