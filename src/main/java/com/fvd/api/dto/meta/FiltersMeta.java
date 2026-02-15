package com.fvd.api.dto.meta;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Available filter values for subjects, versions, and extensions guidance.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class FiltersMeta {

    public List<String> subjects;
    public List<String> versions;
    public String extensionsNote;

}
