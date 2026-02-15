package com.fvd.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AsciiDocCleanerTest {

    @Test
    void stripsIncludeDirective() {
        assertThat(AsciiDocCleaner.clean("include::_attributes.adoc[]")).isEmpty();
    }

    @Test
    void stripsCommentBlock() {
        String input = "before\n////\ncomment\n////\nafter";
        assertThat(AsciiDocCleaner.clean(input)).isEqualTo("before\n\nafter");
    }

    @Test
    void stripsAttributeDeclaration() {
        assertThat(AsciiDocCleaner.clean(":categories: web, rest")).isEmpty();
    }

    @Test
    void stripsIfdefEndif() {
        String input = "ifdef::env[]\ntext\nendif::[]";
        assertThat(AsciiDocCleaner.clean(input)).isEqualTo("text");
    }

    @Test
    void stripsBlockAttributeId() {
        assertThat(AsciiDocCleaner.clean("[id=\"getting-started\"]")).isEmpty();
    }

    @Test
    void stripsBlockAttributeRole() {
        assertThat(AsciiDocCleaner.clean("[.discrete]")).isEmpty();
    }

    @Test
    void extractsXrefLinkText() {
        assertThat(AsciiDocCleaner.clean("see xref:security.adoc[Security Guide]"))
                .isEqualTo("see Security Guide");
    }

    @Test
    void extractsLinkText() {
        assertThat(AsciiDocCleaner.clean("visit link:https://example.com[Example]"))
                .isEqualTo("visit Example");
    }

    @Test
    void extractsInlineXrefText() {
        assertThat(AsciiDocCleaner.clean("see <<security,Security section>>"))
                .isEqualTo("see Security section");
    }

    @Test
    void handlesEmptyXref() {
        assertThat(AsciiDocCleaner.clean("xref:path.adoc[]")).isEmpty();
    }

    @Test
    void preservesNormalText() {
        assertThat(AsciiDocCleaner.clean("Hello world")).isEqualTo("Hello world");
    }

    @Test
    void handlesNullInput() {
        assertThat(AsciiDocCleaner.clean(null)).isEmpty();
    }

    @Test
    void collapsesBlanksLines() {
        assertThat(AsciiDocCleaner.clean("a\n\n\n\nb")).isEqualTo("a\n\nb");
    }

    @Test
    void stripsSourceBlockAttribute() {
        assertThat(AsciiDocCleaner.clean("[source,java]")).isEmpty();
    }

    @Test
    void handlesMixedArtifacts() {
        String input = "include::_attributes.adoc[]\n" +
                ":categories: web\n" +
                "This guide covers xref:security.adoc[Security] features.\n" +
                "ifdef::env[]\nvisible\nendif::[]";
        String result = AsciiDocCleaner.clean(input);
        assertThat(result).contains("This guide covers Security features.");
        assertThat(result).doesNotContain("include::");
        assertThat(result).doesNotContain(":categories:");
        assertThat(result).doesNotContain("ifdef::");
        assertThat(result).doesNotContain("xref:");
    }

    // --- New tests for Feature 85 ---

    @Test
    void stripsAttributeReference() {
        assertThat(AsciiDocCleaner.clean("Version is {quarkus-version} today"))
                .isEqualTo("Version is  today");
    }

    @Test
    void preservesLoneCurlyBrace() {
        assertThat(AsciiDocCleaner.clean("a { b } c")).isEqualTo("a { b } c");
    }

    @Test
    void stripsImageMacroBlock() {
        assertThat(AsciiDocCleaner.clean("image::images/architecture.png[Architecture diagram]"))
                .isEmpty();
    }

    @Test
    void stripsInlineImageMacro() {
        assertThat(AsciiDocCleaner.clean("See image:icon.png[icon] for details"))
                .isEqualTo("See  for details");
    }

    @Test
    void preservesWordImage() {
        assertThat(AsciiDocCleaner.clean("Build a container image for deployment"))
                .isEqualTo("Build a container image for deployment");
    }

    @Test
    void stripsIconMacro() {
        assertThat(AsciiDocCleaner.clean("icon:lock[] Secured endpoint"))
                .isEqualTo("Secured endpoint");
    }

    @Test
    void stripsBlockAnchor() {
        assertThat(AsciiDocCleaner.clean("[[security-overview]]\nSome content"))
                .isEqualTo("Some content");
    }

    @Test
    void preservesDoubleBracketsInProse() {
        // Double brackets inline (not on own line) are not stripped by BLOCK_ANCHOR
        assertThat(AsciiDocCleaner.clean("text with [[note]] inline"))
                .isEqualTo("text with [[note]] inline");
    }

    @Test
    void stripsShorthandAnchor() {
        assertThat(AsciiDocCleaner.clean("[#my-anchor]\nSome content"))
                .isEqualTo("Some content");
    }

    @Test
    void stripsCalloutMarker() {
        assertThat(AsciiDocCleaner.clean("some code <1> explanation"))
                .isEqualTo("some code  explanation");
    }

    @Test
    void stripsMultipleCalloutMarkers() {
        assertThat(AsciiDocCleaner.clean("<1> First\n<2> Second"))
                .isEqualTo("First\n Second");
    }

    @Test
    void preservesNonDigitAngleBrackets() {
        // <b> is not a callout marker (not a digit)
        assertThat(AsciiDocCleaner.clean("use <b> for bold")).isEqualTo("use <b> for bold");
    }

    @Test
    void stripsDotCalloutMarker() {
        assertThat(AsciiDocCleaner.clean("some code <.> auto-number"))
                .isEqualTo("some code  auto-number");
    }

    @Test
    void stripsAdmonitionMarkerKeepsText() {
        assertThat(AsciiDocCleaner.clean("NOTE: This feature requires Java 21."))
                .isEqualTo("This feature requires Java 21.");
    }

    @Test
    void stripsAllAdmonitionMarkerTypes() {
        assertThat(AsciiDocCleaner.clean("TIP: Use quarkus-oidc")).isEqualTo("Use quarkus-oidc");
        assertThat(AsciiDocCleaner.clean("WARNING: Do not expose admin")).isEqualTo("Do not expose admin");
        assertThat(AsciiDocCleaner.clean("IMPORTANT: Configure TLS")).isEqualTo("Configure TLS");
        assertThat(AsciiDocCleaner.clean("CAUTION: This API is experimental")).isEqualTo("This API is experimental");
    }

    @Test
    void stripsTableDelimiter() {
        String input = "|===\n| Feature | Status\n| OIDC | Stable\n|===";
        String result = AsciiDocCleaner.clean(input);
        assertThat(result).doesNotContain("|===");
    }

    @Test
    void stripsTableCellSeparator() {
        String input = "| Feature | Status";
        String result = AsciiDocCleaner.clean(input);
        assertThat(result).doesNotStartWith("|");
        assertThat(result).contains("Feature");
        assertThat(result).contains("Status");
    }

    @Test
    void tableCellPreservesTextContent() {
        String input = "| Quarkus supports reactive | Yes it does";
        String result = AsciiDocCleaner.clean(input);
        assertThat(result).contains("Quarkus supports reactive");
        assertThat(result).contains("Yes it does");
    }

    @Test
    void stripsSingleLineComment() {
        assertThat(AsciiDocCleaner.clean("// This is a comment\nReal content"))
                .isEqualTo("Real content");
    }

    @Test
    void doesNotStripUrlsContainingDoubleSlash() {
        assertThat(AsciiDocCleaner.clean("Visit https://example.com for details"))
                .isEqualTo("Visit https://example.com for details");
    }

    @Test
    void doesNotStripMidLineDoubleSlash() {
        assertThat(AsciiDocCleaner.clean("See http://localhost:8080/api for details"))
                .isEqualTo("See http://localhost:8080/api for details");
    }

    @Test
    void doesNotStripTripleSlashLine() {
        assertThat(AsciiDocCleaner.clean("///\nReal content"))
                .isEqualTo("///\nReal content");
    }

    @Test
    void stripsCommentLineButNotCommentBlockDelimiter() {
        // //// is a comment block delimiter, not a single-line comment
        // The SINGLE_LINE_COMMENT pattern requires //\s+ so //// won't match
        String input = "////\nblock comment\n////";
        String result = AsciiDocCleaner.clean(input);
        // The COMMENT_BLOCK pattern handles this, not SINGLE_LINE_COMMENT
        assertThat(result).isEmpty();
    }

    @Test
    void stripsInlineAnchorRefWithoutText() {
        assertThat(AsciiDocCleaner.clean("see <<security-overview>> for details"))
                .isEqualTo("see  for details");
    }

    @Test
    void preservesInlineXrefWithText() {
        // <<anchor,text>> should keep the text via INLINE_XREF
        assertThat(AsciiDocCleaner.clean("see <<security,Security Overview>> for details"))
                .isEqualTo("see Security Overview for details");
    }

    @Test
    void stripsRoleAttribute() {
        assertThat(AsciiDocCleaner.clean("[.role-name]\nThis paragraph has a role."))
                .isEqualTo("This paragraph has a role.");
    }

    @Test
    void stripsSourceAttributeVariants() {
        assertThat(AsciiDocCleaner.clean("[source,java]")).isEmpty();
        assertThat(AsciiDocCleaner.clean("[listing]")).isEmpty();
        assertThat(AsciiDocCleaner.clean("[literal]")).isEmpty();
        assertThat(AsciiDocCleaner.clean("[verse]")).isEmpty();
        assertThat(AsciiDocCleaner.clean("[quote]")).isEmpty();
        assertThat(AsciiDocCleaner.clean("[sidebar]")).isEmpty();
        assertThat(AsciiDocCleaner.clean("[example]")).isEmpty();
        assertThat(AsciiDocCleaner.clean("[passthrough]")).isEmpty();
    }

    @Test
    void stripsAdmonitionBlock() {
        assertThat(AsciiDocCleaner.clean("[NOTE]\nAdmonition content")).isEqualTo("Admonition content");
        assertThat(AsciiDocCleaner.clean("[TIP]\nTip content")).isEqualTo("Tip content");
        assertThat(AsciiDocCleaner.clean("[WARNING]\nWarning content")).isEqualTo("Warning content");
        assertThat(AsciiDocCleaner.clean("[IMPORTANT]\nImportant content")).isEqualTo("Important content");
        assertThat(AsciiDocCleaner.clean("[CAUTION]\nCaution content")).isEqualTo("Caution content");
    }

    @Test
    void stripsInlineAnchorMacro() {
        assertThat(AsciiDocCleaner.clean("anchor:my-id[My Anchor] some text"))
                .isEqualTo("some text");
    }

    @Test
    void xrefTextPreservedAfterAllPatternsApplied() {
        String input = "include::_includes/prereqs.adoc[]\n" +
                ":description: Security guide\n" +
                "[[security-overview]]\n" +
                "See xref:security-oidc.adoc[OIDC guide] for details.\n" +
                "NOTE: This is important.\n" +
                "image::diagram.png[Diagram]\n" +
                "<1> First callout";
        String result = AsciiDocCleaner.clean(input);
        assertThat(result).contains("OIDC guide");
        assertThat(result).contains("This is important.");
        assertThat(result).doesNotContain("include::");
        assertThat(result).doesNotContain(":description:");
        assertThat(result).doesNotContain("[[security-overview]]");
        assertThat(result).doesNotContain("image::");
        assertThat(result).doesNotContain("<1>");
        assertThat(result).doesNotContain("NOTE:");
    }

    @Test
    void mixedMarkupTestWithAllNewPatterns() {
        String input = """
                include::_includes/prerequisites.adoc[]
                :sectnums:
                :description: Guide to configuring security
                
                [[security-overview]]
                [#security-overview]
                
                TIP: Use the quarkus-oidc extension for OIDC support.
                NOTE: This feature requires Java 21.
                WARNING: Do not expose admin endpoints publicly.
                
                [source,java]
                
                |===
                | Feature | Status
                | OIDC | Stable
                |===
                
                See xref:security-oidc.adoc[OIDC guide] for details.
                See <<security-overview>> for the overview.
                
                icon:lock[] Secured endpoint
                
                [.role-name]
                This paragraph has a role.
                
                // Single-line comment
                <1> First callout
                <2> Second callout
                
                image::images/architecture.png[Architecture diagram]
                
                Version is {quarkus-version} today.
                """;
        String result = AsciiDocCleaner.clean(input);
        // Content should be preserved
        assertThat(result).contains("Use the quarkus-oidc extension for OIDC support.");
        assertThat(result).contains("This feature requires Java 21.");
        assertThat(result).contains("Do not expose admin endpoints publicly.");
        assertThat(result).contains("OIDC guide");
        assertThat(result).contains("Secured endpoint");
        assertThat(result).contains("This paragraph has a role.");
        assertThat(result).contains("Version is  today.");
        // Noise should be removed
        assertThat(result).doesNotContain("include::");
        assertThat(result).doesNotContain(":sectnums:");
        assertThat(result).doesNotContain(":description:");
        assertThat(result).doesNotContain("[[security-overview]]");
        assertThat(result).doesNotContain("[#security-overview]");
        assertThat(result).doesNotContain("TIP:");
        assertThat(result).doesNotContain("NOTE:");
        assertThat(result).doesNotContain("WARNING:");
        assertThat(result).doesNotContain("[source,java]");
        assertThat(result).doesNotContain("|===");
        assertThat(result).doesNotContain("xref:");
        assertThat(result).doesNotContain("<<security-overview>>");
        assertThat(result).doesNotContain("icon:");
        assertThat(result).doesNotContain("[.role-name]");
        assertThat(result).doesNotContain("// Single-line comment");
        assertThat(result).doesNotContain("<1>");
        assertThat(result).doesNotContain("<2>");
        assertThat(result).doesNotContain("image::");
        assertThat(result).doesNotContain("{quarkus-version}");
    }
}
