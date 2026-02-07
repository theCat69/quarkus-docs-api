package com.fvd.github.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Base64;

@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubApiFile {
    public String name;
    public String path;
    public String sha;
    public String content;
    public String encoding;

    public String decodeContent() {
        if ("base64".equals(encoding) && content != null) {
            String cleaned = content.replaceAll("\\s", "");
            return new String(Base64.getDecoder().decode(cleaned));
        }
        return content != null ? content : "";
    }
}
