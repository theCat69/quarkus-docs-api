package com.fvd.docs.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fvd.common.validators.InputValidator;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.exceptions.UpstreamException;
import com.fvd.github.services.GitHubService;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.Base64;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor
public class DocService {

    private final DocStore docStore;
    private final GitHubService gitHubService;
    private final ObjectMapper objectMapper;

    public String getOrFetchDoc(String version, String path) {
        InputValidator.validateVersion(version);
        InputValidator.validatePath(path);
        Optional<String> cached = docStore.read(version, path);
        if (cached.isPresent()) {
            return cached.get();
        }
        String jsonResponse = gitHubService.fetchFileContent(path, version);
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
