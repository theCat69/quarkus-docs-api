package com.fvd.common.resources;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    public int status;
    public String message;
}
