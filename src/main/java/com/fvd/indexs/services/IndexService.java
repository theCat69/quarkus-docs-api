package com.fvd.indexs.services;

import com.fvd.common.validators.InputValidator;
import com.fvd.github.clients.GitHubClient;
import com.fvd.indexs.stores.IndexStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class IndexService {

    private final IndexStore indexStore;
    private final GitHubClient gitHubClient;

    @Inject
    public IndexService(IndexStore indexStore, GitHubClient gitHubClient) {
        this.indexStore = indexStore;
        this.gitHubClient = gitHubClient;
    }

    public String getOrFetchIndex(String version) {
        InputValidator.validateVersion(version);
        Optional<String> cached = indexStore.readRaw(version);
        if (cached.isPresent()) {
            return cached.get();
        }
        String json = gitHubClient.fetchIndex(version);
        indexStore.writeRaw(version, json);
        return json;
    }
}
