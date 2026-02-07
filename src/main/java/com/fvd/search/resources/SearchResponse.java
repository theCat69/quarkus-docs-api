package com.fvd.search.resources;

import java.util.List;

public class SearchResponse<T> {

    public List<T> results;

    public SearchResponse() {
    }

    public SearchResponse(List<T> results) {
        this.results = results;
    }
}
