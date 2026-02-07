# Quarkus WireMock Extension (io.quarkiverse.wiremock) - v1.5.3

> Sources: https://github.com/quarkiverse/quarkus-wiremock (tag 1.5.3, Dec 23 2025)
> Docs: https://docs.quarkiverse.io/quarkus-wiremock/dev/index.html
> Fetched: 2026-02-07

## 1. Coordinates & Compatibility

### Maven
```xml
<!-- Core runtime (Dev Service) - use "provided" scope since it only runs in dev/test -->
<dependency>
    <groupId>io.quarkiverse.wiremock</groupId>
    <artifactId>quarkus-wiremock</artifactId>
    <version>1.5.3</version>
    <scope>provided</scope>
</dependency>

<!-- Test helper (for @ConnectWireMock, WireMock injection) - test scope -->
<dependency>
    <groupId>io.quarkiverse.wiremock</groupId>
    <artifactId>quarkus-wiremock-test</artifactId>
    <version>1.5.3</version>
    <scope>test</scope>
</dependency>
```

### Gradle
```groovy
// Core runtime (Dev Service)
implementation("io.quarkiverse.wiremock:quarkus-wiremock:1.5.3")
// Test helper (for @ConnectWireMock annotation and WireMock field injection)
testImplementation("io.quarkiverse.wiremock:quarkus-wiremock-test:1.5.3")
```

### Compatibility Matrix
| Quarkus WireMock | WireMock | Quarkus Platform | JVM     |
|------------------|----------|------------------|---------|
| 1.x.x            | 3.x      | 3.27.x (LTS)    | 17 - 25 |

---

## 2. How Dev Services Auto-Start Works

The extension starts WireMock as a **Quarkus Dev Service** - it runs **in-process** (no Docker needed).
It auto-starts in `dev` and `test` mode.

### Default Configuration Properties
```properties
quarkus.wiremock.devservices.enabled=true          # enabled by default
quarkus.wiremock.devservices.reload=true            # restart on file changes
quarkus.wiremock.devservices.files-mapping=src/test/resources  # root for mappings/ and __files/
quarkus.wiremock.devservices.global-response-templating=false
quarkus.wiremock.devservices.extension-scanning-enabled=false
# quarkus.wiremock.devservices.port=              # if omitted, random port is assigned
```

### Port Behavior
- **By default**: random port. Access it via `WireMockConfigKey.PORT` = `"quarkus.wiremock.devservices.port"`
- **Fixed port**: set `quarkus.wiremock.devservices.port=60000` (only ports 1025-65535 allowed)
- The port value is propagated as a Dev Services config property accessible via `ConfigProvider` or `DevServicesContext`

### All Configuration Keys (from `WireMockConfigKey.java`)
```java
package io.quarkiverse.wiremock.devservice;

public class WireMockConfigKey {
    static final String PREFIX = "quarkus.wiremock.devservices";
    public static final String PORT = PREFIX + ".port";                          // "quarkus.wiremock.devservices.port"
    public static final String RELOAD = PREFIX + ".reload";                      // "quarkus.wiremock.devservices.reload"
    public static final String FILES_MAPPING = PREFIX + ".files-mapping";        // "quarkus.wiremock.devservices.files-mapping"
    public static final String GLOBAL_RESPONSE_TEMPLATING = PREFIX + ".global-response-templating";
}
```

---

## 3. Annotations & Test Setup

### @ConnectWireMock
```java
package io.quarkiverse.wiremock.devservice;

// This is a meta-annotation that wraps @QuarkusTestResource
@QuarkusTestResource(value = WireMockServerConnector.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface ConnectWireMock {
}
```

### Usage: @QuarkusTest with @ConnectWireMock
```java
import io.quarkus.test.junit.QuarkusTest;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkiverse.wiremock.devservice.WireMockConfigKey;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

@QuarkusTest
@ConnectWireMock
class MyServiceTest {

    // Auto-injected by @ConnectWireMock (field injection, no @Inject needed)
    WireMock wiremock;

    // Access the WireMock port via config
    @ConfigProperty(name = WireMockConfigKey.PORT)
    Integer wiremockPort;

    @Test
    void testProgrammaticStub() {
        wiremock.register(get(urlEqualTo("/api/hello"))
            .willReturn(aResponse().withStatus(200).withBody("mocked")));
        // ... call your service that hits http://localhost:{port}/api/hello
    }
}
```

### Usage: @QuarkusIntegrationTest
```java
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.DevServicesContext;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkiverse.wiremock.devservice.WireMockConfigKey;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.eclipse.microprofile.config.ConfigProvider;

@QuarkusIntegrationTest
@ConnectWireMock
class MyServiceIT {
    WireMock wiremock;                    // injected by @ConnectWireMock
    DevServicesContext devServicesContext; // injected by Quarkus

    @Test
    void test() {
        int port = Integer.parseInt(devServicesContext.devServicesProperties().get(WireMockConfigKey.PORT));
        // or: int port = ConfigProvider.getConfig().getValue(WireMockConfigKey.PORT, Integer.class);
    }
}
```

**Key injection rules:**
- `WireMock` field: injected ONLY when class is annotated with `@ConnectWireMock`
- `DevServicesContext` field: injected by Quarkus in integration tests
- Both use **field injection** (no `@Inject` annotation needed)

---

## 4. JSON File-Based Mapping (Non-Programmatic Stubs)

### Directory Structure
The root is controlled by `quarkus.wiremock.devservices.files-mapping` (default: `src/test/resources`).
Inside that root, WireMock expects:

```
src/test/resources/
  mappings/            <-- JSON stub mapping files go here
    my-stub.json
    another-stub.json
  __files/             <-- Response body files go here (referenced from mappings)
    response-body.json
    some-response.xml
```

### Example Mapping File: `src/test/resources/mappings/basic.json`
```json
{
  "request": {
    "method": "GET",
    "url": "/basic"
  },
  "response": {
    "status": 200,
    "body": "Everything was just fine!"
  }
}
```

### Example with Response Templating: `src/test/resources/mappings/template.json`
```json
{
  "request": {
    "method": "GET",
    "url": "/template"
  },
  "response": {
    "status": 200,
    "body": "Everything was just fine from {{ request.port }}!"
  }
}
```

### Using `__files/` for External Response Bodies
```json
{
  "request": {
    "method": "GET",
    "url": "/api/repos"
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "bodyFileName": "github-repos-response.json"
  }
}
```
The `bodyFileName` path is relative to `src/test/resources/__files/`.

### Classpath-Based Mapping
You can also load mappings from the classpath:
```properties
quarkus.wiremock.devservices.files-mapping=classpath:other
```
This looks for `other/mappings/` and `other/__files/` on the classpath.

---

## 5. Making Application Config Point to WireMock Server URL

Use **Quarkus property expressions** (variable interpolation) to reference the WireMock port.

### In `application.properties` (or `application-test.properties`)
```properties
# Your custom app property pointing to WireMock
app.github.api-base=http://localhost:${quarkus.wiremock.devservices.port}

# Another example
custom.config.wiremock.url=http://localhost:${quarkus.wiremock.devservices.port}/mock-me
```

### Usage in Test
```java
@QuarkusTest
@ConnectWireMock
class GitHubClientTest {

    WireMock wiremock;

    @ConfigProperty(name = "app.github.api-base")
    String githubApiBase;  // resolves to "http://localhost:<wiremock-port>"

    @Test
    void testGitHubClientCallsWireMock() {
        wiremock.register(get(urlEqualTo("/repos/user/repo"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"name\": \"my-repo\"}")));

        // Your REST client configured with app.github.api-base will hit WireMock
        // ...
    }
}
```

### With a Fixed Port (via TestProfile)
```java
class FixedPortProfile implements QuarkusTestProfile {
    public static final String PORT = "60000";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(WireMockConfigKey.PORT, PORT);
    }
}

@QuarkusTest
@ConnectWireMock
@TestProfile(FixedPortProfile.class)
class MyFixedPortTest {
    WireMock wiremock;
    // WireMock will listen on port 60000
}
```

---

## 6. How WireMockServerConnector Works (Internals)

The `@ConnectWireMock` annotation triggers `WireMockServerConnector` which:
1. Implements `DevServicesContext.ContextAware` to receive the dev services config
2. Reads the WireMock port from `devServicesProperties`
3. Creates a `new WireMock(port)` client connected to `localhost:<port>`
4. Calls `WireMock.configureFor(port)` for static API usage
5. Injects the `WireMock` instance into test class fields of type `WireMock`

```java
// Source: WireMockServerConnector.java
public class WireMockServerConnector
        implements QuarkusTestResourceConfigurableLifecycleManager<ConnectWireMock>,
                   DevServicesContext.ContextAware {

    WireMock wiremock;

    @Override
    public Map<String, String> start() {
        return Collections.emptyMap(); // Dev Service already started the server
    }

    @Override
    public void stop() {
        // Dev Service will shut down the server
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(wiremock, new TestInjector.MatchesType(WireMock.class));
    }

    @Override
    public void setIntegrationTestContext(DevServicesContext context) {
        int port = Integer.parseInt(context.devServicesProperties().get(WireMockConfigKey.PORT));
        wiremock = new WireMock(port);
        WireMock.configureFor(port);
        wiremock.getGlobalSettings(); // eagerly verify connection
    }
}
```

---

## 7. Logging Configuration

```properties
# Reduce WireMock server log noise
quarkus.log.category."io.quarkiverse.wiremock.devservice.WireMockServer".level=ERROR

# Enable debug for troubleshooting
quarkus.log.category."io.quarkiverse".level=DEBUG
```

---

## 8. Quick Start Checklist for a Quarkus Gradle Project

1. **Add dependencies** to `build.gradle`:
   ```groovy
   implementation("io.quarkiverse.wiremock:quarkus-wiremock:1.5.3")
   testImplementation("io.quarkiverse.wiremock:quarkus-wiremock-test:1.5.3")
   ```

2. **Create JSON mapping files** under `src/test/resources/mappings/`:
   ```
   src/test/resources/
     mappings/
       github-repos.json     <-- stub definitions
     __files/
       github-repos-body.json <-- response bodies (optional)
   ```

3. **Configure URL property** in `application.properties`:
   ```properties
   app.github.api-base=http://localhost:${quarkus.wiremock.devservices.port}
   ```

4. **Write test**:
   ```java
   @QuarkusTest
   @ConnectWireMock
   class MyTest {
       WireMock wiremock;  // auto-injected

       @Test
       void test() {
           // stubs from mappings/ are auto-loaded
           // or register programmatic stubs:
           wiremock.register(get(urlEqualTo("/api/foo"))
               .willReturn(aResponse().withStatus(200).withBody("bar")));
       }
   }
   ```

---

## Important Notes

- The extension is **in-process** (no Docker/Testcontainers needed)
- Quarkus may print `unrecognized configuration key` warnings for WireMock properties during tests - this is expected and harmless due to the build-time nature of the extension
- `quarkus-wiremock` (core) scope should be `provided` (Maven) or `implementation` (Gradle) since it only runs in dev/test mode
- `quarkus-wiremock-test` is needed ONLY for `@ConnectWireMock` annotation and `WireMock` field injection in tests
- JSON mapping files are automatically loaded at WireMock server startup from the `files-mapping` root
- The `WireMock` field in tests does NOT use `@Inject` - it's injected by the test resource lifecycle manager
