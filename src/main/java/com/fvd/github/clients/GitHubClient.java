package com.fvd.github.clients;

import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.github.exceptions.UpstreamException;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
public class GitHubClient {

    private final HttpClient httpClient;
    private final String githubToken;
    private final String apiBase;
    private final String zipBase;

    public GitHubClient(
            @ConfigProperty(name = "app.github.token") Optional<String> githubToken,
            @ConfigProperty(name = "app.github.api-base",
                    defaultValue = "https://api.github.com/repos/quarkusio/quarkus/contents/") String apiBase,
            @ConfigProperty(name = "app.github.zip-base",
                    defaultValue = "https://github.com/quarkusio/quarkus/archive/refs/heads/") String zipBase) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.githubToken = githubToken.orElse("");
        this.apiBase = apiBase;
        this.zipBase = zipBase;
    }

    public String fetchIndex(String version) {
        String url = apiBase + "docs/src/main/asciidoc?ref=" + version;
        return fetchString(url);
    }

    public String fetchFileContent(String filePath, String version) {
        String url = apiBase + filePath + "?ref=" + version;
        return fetchString(url);
    }

    public InputStream fetchZipStream(String version) {
        String url = zipBase + version + ".zip";
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .GET();
            addAuthHeader(builder);
            HttpResponse<InputStream> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new UpstreamException("GitHub zip download failed with status " + response.statusCode()
                        + " for version: " + version);
            }
            return response.body();
        } catch (IOException e) {
            throw new UpstreamException("Failed to download zip for version: " + version, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamException("Zip download interrupted for version: " + version, e);
        }
    }

    private String fetchString(String url) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET();
            addAuthHeader(builder);
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new DocNotFoundException("Not found: " + url);
            }
            if (response.statusCode() != 200) {
                throw new UpstreamException("GitHub API returned status " + response.statusCode()
                        + " for URL: " + url);
            }
            return response.body();
        } catch (DocNotFoundException | UpstreamException e) {
            throw e;
        } catch (IOException e) {
            throw new UpstreamException("Failed to fetch from GitHub: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamException("Request interrupted: " + url, e);
        }
    }

    private void addAuthHeader(HttpRequest.Builder builder) {
        if (githubToken != null && !githubToken.isBlank()) {
            builder.header("Authorization", "Bearer " + githubToken);
        }
    }
}
