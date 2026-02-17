package com.fvd.common.utils;

import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Builds canonical URLs for Quarkus guide pages and section anchors.
 */
@ApplicationScoped
public class UrlBuilder {

    private static final String BASE_URL = "https://quarkus.io/guides/";
    private static final String ADOC_SUFFIX = ".adoc";
    private static final Pattern APOSTROPHES = Pattern.compile("['`]");
    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9-]");
    private static final Pattern MULTIPLE_DASHES = Pattern.compile("-{2,}");

    /**
     * Builds a URL for a Quarkus guide page with a section anchor.
     *
     * @param page         the guide page name (with or without .adoc extension)
     * @param sectionTitle the section title to convert to an anchor slug, may be null or empty
     * @return the full URL with anchor fragment, or page-level URL if sectionTitle is null/empty
     */
    public String buildUrl(String page, String sectionTitle) {
        String cleanPage = stripAdocExtension(page);
        String slug = toSlug(sectionTitle);
        if (slug.isEmpty()) {
            return BASE_URL + cleanPage;
        }
        return BASE_URL + cleanPage + "#" + slug;
    }

    /**
     * Builds a URL for a Quarkus guide page without a section anchor.
     *
     * @param page the guide page name (with or without .adoc extension)
     * @return the page-level URL
     */
    public String buildUrl(String page) {
        return BASE_URL + stripAdocExtension(page);
    }

    private String stripAdocExtension(String page) {
        if (page != null && page.endsWith(ADOC_SUFFIX)) {
            return page.substring(0, page.length() - ADOC_SUFFIX.length());
        }
        return page;
    }

    /**
     * Converts a section title to a URL-safe anchor slug.
     * Lowercase, replace non-alphanumeric characters with dashes, collapse consecutive dashes,
     * strip leading/trailing dashes.
     */
    public String toSlug(String sectionTitle) {
        if (sectionTitle == null || sectionTitle.isBlank()) {
            return "";
        }
        String lower = sectionTitle.toLowerCase();
        String noApostrophes = APOSTROPHES.matcher(lower).replaceAll("");
        String replaced = NON_SLUG_CHARS.matcher(noApostrophes).replaceAll("-");
        String collapsed = MULTIPLE_DASHES.matcher(replaced).replaceAll("-");
        return collapsed.replaceAll("^-+|-+$", "");
    }
}
