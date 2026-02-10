package com.fvd.indexs.indexers;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
public class ContentIndex {

    public Map<String, List<ContentOccurrence>> wordOccurrences;

}
