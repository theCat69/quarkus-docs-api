package com.fvd.indexs.services;

import com.fvd.common.validators.InputValidator;
import com.fvd.github.clients.GitHubClient;
import com.fvd.indexs.stores.IndexStore;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor
public class IndexService {

    private final IndexStore indexStore;
    private final GitHubClient gitHubClient;

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
