package com.fvd.common;

import java.util.Set;

import lombok.experimental.UtilityClass;

@UtilityClass
public class StopWords {

    public static final Set<String> DEFAULT = Set.of(
            "a", "an", "and", "the", "how", "does", "do", "is", "are", "was",
            "were", "what", "which", "who", "when", "where", "why", "in", "on",
            "at", "to", "for", "with", "from", "by", "of", "about", "explain",
            "show", "me", "work", "works", "working", "please", "your"
    );
}
