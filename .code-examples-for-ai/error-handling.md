# Pattern: Error Handling (domain exception + ExceptionMapper + ProblemDetail)
# Demonstrates: AbstractProblemDetailMapper<T> base class, @Provider annotation,
# RFC 9457 ProblemDetail response shape, and the three standard domain exceptions
# (InvalidInputException → 400, DocNotFoundException → 404, UpstreamException → 502).

```java
// ---- 1. Abstract base mapper (AbstractProblemDetailMapper.java) ----
package com.fvd.common.exceptions;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;

import com.fvd.common.resources.ProblemDetail;

/**
 * Abstract base for RFC 9457 Problem Detail exception mappers.
 * Concrete subclasses must be annotated with {@code @Provider}.
 *
 * @param <T> the exception type to map
 */
public abstract class AbstractProblemDetailMapper<T extends Throwable>
        implements ExceptionMapper<T> {

    @Context
    UriInfo uriInfo;   // @Context injection is fine in ExceptionMapper (not a CDI bean)

    @Override
    public final Response toResponse(T exception) {
        String instance = uriInfo != null ? uriInfo.getPath() : "/api";
        Response.Status status = resolveStatus(exception);
        String title = resolveTitle(exception);
        ProblemDetail problem = ProblemDetail.of(
                status.getStatusCode(), title, getDetail(exception), instance);
        return Response.status(status)
                .entity(problem)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    // Template method pattern — subclasses override these
    protected abstract Response.Status getStatus();
    protected abstract String getTitle();
    protected abstract String getDetail(T exception);
}


// ---- 2. Concrete mapper for InvalidInputException (400) ----
package com.fvd.common.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

// @Provider registers this as a JAX-RS ExceptionMapper — no additional CDI annotation needed
@Provider
public class InvalidInputExceptionMapper extends AbstractProblemDetailMapper<InvalidInputException> {

    @Override
    protected Response.Status getStatus() { return Response.Status.BAD_REQUEST; }

    @Override
    protected String getTitle() { return "Bad Request"; }

    @Override
    protected String getDetail(InvalidInputException exception) {
        return exception.getMessage();   // Safe: InvalidInputException is a domain exception
    }
}


// ---- 3. ProblemDetail DTO (RFC 9457 shape) ----
// The response JSON always has: type, title, status, detail, instance.
// Example JSON:
// {
//   "type": "about:blank",
//   "title": "Bad Request",
//   "status": 400,
//   "detail": "Query parameter 'q' must not be empty",
//   "instance": "/api/search"
// }


// ---- 4. Throwing a domain exception in a resource ----
public ChunkSearchResponse search(@QueryParam("q") String q, ...) {
    if (q == null || q.isBlank()) {
        // InvalidInputException is caught by InvalidInputExceptionMapper → 400 response
        throw new InvalidInputException("Query parameter 'q' must not be empty");
    }
    // ...
}
```
