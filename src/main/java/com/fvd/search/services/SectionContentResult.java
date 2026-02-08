package com.fvd.search.services;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class SectionContentResult {

    public String path;
    public String title;
    public int startLine;
    public int endLine;
    public String content;

}
