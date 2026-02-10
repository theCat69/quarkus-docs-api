package com.fvd.quarkiverse.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fvd.quarkiverse.models.AntoraPlaybook;
import com.fvd.quarkiverse.models.ContentSource;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class AntoraPlaybookParser {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile("https://github\\.com/([^/]+)/([^/]+)");
    private static final Pattern WILDCARD_PATTERN = Pattern.compile("[*?]");
    private static final String DEFAULT_BRANCH = "main";

    public List<ResolvedContentSource> parse(String yamlContent) {
        AntoraPlaybook playbook;
        try {
            playbook = YAML_MAPPER.readValue(yamlContent, AntoraPlaybook.class);
        } catch (Exception e) {
            log.error("Failed to parse antora-playbook.yml", e);
            return List.of();
        }

        if (playbook.content == null || playbook.content.sources == null) {
            return List.of();
        }

        List<ResolvedContentSource> results = new ArrayList<>();
        for (ContentSource source : playbook.content.sources) {
            resolveSource(source).ifPresent(results::add);
        }
        return results;
    }

    private java.util.Optional<ResolvedContentSource> resolveSource(ContentSource source) {
        if (source.url == null) {
            log.warn("Skipping content source with null URL");
            return java.util.Optional.empty();
        }

        var matcher = GITHUB_URL_PATTERN.matcher(source.url);
        if (!matcher.matches()) {
            log.warn("Skipping non-GitHub URL: {}", source.url);
            return java.util.Optional.empty();
        }

        String org = matcher.group(1);
        String repo = matcher.group(2);
        String branch = resolveBranch(source.branches);
        String startPath = source.startPath != null ? source.startPath : "";

        //sanitizing .git from repo name
        if(repo.endsWith(".git")) {
            repo = repo.replaceAll(".git", "");
        }

        return java.util.Optional.of(new ResolvedContentSource(org, repo, branch, startPath, repo));
    }

    String resolveBranch(Object branches) {
        if (branches == null) {
            return DEFAULT_BRANCH;
        }

        if (branches instanceof String branchStr) {
            return isConcreteBranch(branchStr) ? branchStr : DEFAULT_BRANCH;
        }

        if (branches instanceof Number branchNum) {
            return branchNum.toString();
        }

        if (branches instanceof List<?> branchList) {
            for (Object item : branchList) {
                String branchStr = toBranchString(item);
                if (branchStr != null && isConcreteBranch(branchStr)) {
                    return branchStr;
                }
            }
            return DEFAULT_BRANCH;
        }

        return DEFAULT_BRANCH;
    }

    private String toBranchString(Object item) {
        if (item instanceof String s) {
            return s;
        }
        if (item instanceof Number n) {
            return n.toString();
        }
        return null;
    }

    private boolean isConcreteBranch(String branch) {
        if (branch.startsWith("/") && branch.endsWith("/")) {
            return false;
        } else if(branch.equals("HEAD")) {
            return false;
        }
        return !WILDCARD_PATTERN.matcher(branch).find();
    }
}
