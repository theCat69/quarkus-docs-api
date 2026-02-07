package com.fvd.search.resources;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse<T> {

    public List<T> results;

}
