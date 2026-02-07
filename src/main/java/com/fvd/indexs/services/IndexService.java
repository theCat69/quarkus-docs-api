package com.fvd.indexs.services;

import com.fvd.common.validators.InputValidator;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.github.services.GitHubService;
import com.fvd.indexs.stores.IndexStore;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor
public class IndexService {

    private final IndexStore indexStore;
    private final GitHubService gitHubService;

    public List<GithubApiIndex> getOrFetchIndex(String version) {
        InputValidator.validateVersion(version);
        Optional<List<GithubApiIndex>> cached = indexStore.read(version);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<GithubApiIndex> index = gitHubService.fetchIndex(version);
        indexStore.write(version, index);
        return index;
    }
}
