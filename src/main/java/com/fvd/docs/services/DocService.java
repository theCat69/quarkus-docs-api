package com.fvd.docs.services;

import com.fvd.common.validators.InputValidator;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GithubApiFile;
import com.fvd.github.services.GitHubService;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor
public class DocService {

    private final DocStore docStore;
    private final GitHubService gitHubService;

    public String getOrFetchDoc(String version, String path) {
        InputValidator.validateVersion(version);
        InputValidator.validatePath(path);
        Optional<String> cached = docStore.read(version, path);
        if (cached.isPresent()) {
            return cached.get();
        }
        GithubApiFile file = gitHubService.fetchFileContent(path, version);
        String content = file.decodeContent();
        docStore.write(version, path, content);
        return content;
    }
}
