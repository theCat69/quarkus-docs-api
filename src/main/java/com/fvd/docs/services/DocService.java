package com.fvd.docs.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fvd.common.validators.InputValidator;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.clients.GitHubClient;
import com.fvd.github.exceptions.UpstreamException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Base64;
import java.util.Optional;

@ApplicationScoped
public class DocService {

    private final DocStore docStore;
    private final GitHubClient gitHubClient;
    private final ObjectMapper objectMapper;

    @Inject
    public DocService(DocStore docStore, GitHubClient gitHubClient, ObjectMapper objectMapper) {
        this.docStore = docStore;
        this.gitHubClient = gitHubClient;
        this.objectMapper = objectMapper;
    }

    public String getOrFetchDoc(String version, String path) {
        InputValidator.validateVersion(version);
        InputValidator.validatePath(path);
        Optional<String> cached = docStore.read(version, path);
        if (cached.isPresent()) {
            return cached.get();
        }
        String jsonResponse = gitHubClient.fetchFileContent(path, version);
        String content = decodeContent(jsonResponse, path);
        docStore.write(version, path, content);
        return content;
    }

    String decodeContent(String jsonResponse, String path) {
        try {
            JsonNode node = objectMapper.readTree(jsonResponse);
            String encoding = node.has("encoding") ? node.get("encoding").asText() : "";
            String rawContent = node.has("content") ? node.get("content").asText() : "";
            if ("base64".equals(encoding)) {
                String cleaned = rawContent.replaceAll("\\s", "");
                return new String(Base64.getDecoder().decode(cleaned));
            }
            return rawContent;
        } catch (Exception e) {
            throw new UpstreamException("Failed to decode content for: " + path, e);
        }
    }
}
