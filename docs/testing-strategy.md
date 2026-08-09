# Testing Strategy

This project uses different test levels for different questions. The central rule is:

> Do not mock the thing the test is intended to validate.

A unit test mocks an outgoing port because it validates one class. A web slice mocks the service called by a controller because it validates MVC and Spring Security. An integration test uses real Spring components and adapters, while replacing only systems outside the application process. Smoke and end-to-end tests replace nothing because they exercise an already-running deployment.

## Executable taxonomy

| Level | Command | Runtime / ownership | Mocked or simulated | Real |
| --- | --- | --- | --- | --- |
| Unit | `mise run test` | Gradle/JUnit owns the test JVM; no Docker | Outgoing ports and repositories | Class under test |
| Web slice | `mise run test` | Gradle/Spring owns the in-process test context; no Docker | Service called by the controller | MVC mapping and Spring Security |
| Integration | `mise run test:integration` | The module test owns its in-process Spring/JVM environment and controlled stubs; no Docker | External HTTP systems only | Spring context and production adapters |
| Architecture | `./gradlew :resource-server:archTest :architecture-tests:test` | Gradle performs static analysis; no application runtime or Docker | Nothing | Static dependency analysis |
| Mutation analysis | `mise run test:mutation` | PIT owns forked test JVMs; no Docker | The boundaries already declared by selected tests | PIT changes production behavior to measure test strength |
| Browser OAuth mock | `mise run mock:playwright` or `mise run mock:playwright:ui` | Playwright owns the client JVM and Node mock services; no Docker | Authorization server and resource server HTTP systems | Chromium, the production client server, PKCE, callback/session handling and logout handler |
| Smoke | `mise run compose:smoke` or `mise run k3d:smoke` | Local tasks target an existing Compose or k3d runtime; GitHub Actions owns its ephemeral Compose stack | Nothing | Essential availability and security checks against an active runtime |
| M2M end to end | `mise run compose:e2e` or `mise run k3d:e2e` | Local tasks target an existing Compose or k3d runtime; GitHub Actions owns its ephemeral Compose stack | Nothing | Cucumber feature with real token issuance and protected API call |
| Browser OAuth end to end | `mise run compose:playwright`, `mise run compose:playwright:ui`, `mise run k3d:playwright` or `mise run k3d:playwright:ui` | Local tasks target an existing Compose or k3d runtime; GitHub Actions owns its ephemeral Compose stack | Nothing | Chromium crosses the three applications in an already-running stack |

No current test uses Testcontainers. Module integration tests create and own their in-process Spring/JVM test environment and any controlled HTTP stub they need; they do not ask Docker to provision dependencies. Smoke and end-to-end tests instead target real application processes in Compose or k3d.

`mise run test` remains the fast flow. The root Gradle `test` selector runs the global `architecture-tests:test` task because it is the standard test task of that module, and `resource-server:test` finalizes with its module-specific `archTest`. Both architecture suites are static and do not require Docker.

`test`, `check`, and `build` do not execute `integrationTest`, mutation analysis, smoke tests, or end-to-end tests. `mise run test:all` adds all three module integration suites and both architecture suites, but still does not contact Compose or k3d. Unit, slice, architecture, integration, coverage and mutation tasks all run without Docker.

## BDD vocabulary in tests

Use Mockito's BDD API (`given(...).willReturn(...)`) when preparing a mocked collaborator. Given/When/Then comments are optional signposts, not a template that every test must fill.

- Use `// Given` when setup is substantial or when a stub is important to the scenario.
- Use separate `// When` and `// Then` comments when the action produces an intermediate result that is asserted later.
- Use AssertJ for every Java assertion. Capture MockMvc responses with `andReturn()` instead of using Hamcrest or `andExpect(...)` matchers.
- Omit the comments when the method name and one or two statements already explain the scenario. Do not add an empty or artificial Given just to complete the trio.
- Keep setup strict. Do not use lenient Mockito stubs to hide unused preparation.

Spring test dependencies use constructor injection. Each participating source set sets `spring.test.constructor.autowire.mode=all` in
`junit-platform.properties`: JUnit creates test instances, so this lets Spring resolve unannotated constructor parameters from the already-defined
test context. It does not enable component scanning; `@SpringJUnitConfig`, `@SpringBootTest`, or the selected test slice still defines that context.

For classes with constructor dependencies, declare the dependency with `@Mock` and construct the subject explicitly in `@BeforeEach`. Do not use `@InjectMocks`; the explicit constructor call makes the composition point visible.

```java
@ExtendWith(MockitoExtension.class)
class ListTasksTest {

    @Mock
    private TaskRepository taskRepository;

    private ListTasks listTasks;

    @BeforeEach
    void setUp() {
        listTasks = new ListTasks(taskRepository);
    }

    @Test
    void returnsTasksProvidedByTheRepositoryPort() {
        // Given
        given(taskRepository.findAll()).willReturn(List.of(new Task("Repository task")));

        // When
        List<Task> result = listTasks.execute();

        // Then
        assertThat(result).extracting(Task::title).containsExactly("Repository task");
    }
}
```

A web slice uses the same vocabulary while letting Spring construct the controller:

```java
@WebMvcTest(TaskRestController.class)
class TaskRestControllerTest {

    @MockitoBean
    private ListTasks listTasks;

    @Test
    void rendersTasksForAUserWithTheRequiredScope() throws Exception {
        // Given
        given(listTasks.execute()).willReturn(List.of(new Task("Task 1")));

        // When
        var response = mockMvc.perform(get("/tasks").with(jwt().authorities(apiRead())))
                .andReturn().getResponse();

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }
}
```

## Unit service versus service integration

The two `ListTasks` tests deliberately answer different questions:

| Test | Composition | What a failure means |
| --- | --- | --- |
| `ListTasksTest` | `TaskRepository` is a Mockito mock; `ListTasks` is constructed in `@BeforeEach` | The application service does not use its port correctly |
| `ListTasksIntegrationTest` | Spring creates `ListTasks` and `InMemoryTaskRepository`; no mocks | The service and real adapter do not compose or preserve the three-task behavior |

The repository choices are also intentionally distinct:

- **Repository mock:** a unit-test boundary. It returns scenario-specific data and proves how the class under test uses the port.
- **Real in-memory adapter:** production code used by the resource server. It proves component wiring and preserves the three existing tasks without database infrastructure.

There is no database adapter in the current domain. Adding a disposable database would test infrastructure that the applications do not use.

## Module coverage

### Resource server

- `AudienceValidatorTest` covers disabled validation, any matching audience, a missing audience, and unexpected audiences.
- `ListTasksTest` is a pure unit test with a mocked `TaskRepository` port.
- `TaskRestControllerTest` is a `@WebMvcTest` slice. `ListTasks` is a `@MockitoBean`; MVC and the production security configuration are real. It covers `401`, `403`, and `200`.
- `ListTasksIntegrationTest` uses the real in-memory repository adapter without mocks.
- `ResourceServerIntegrationTest` loads the full application and uses a simulated OAuth/JWKS HTTP server. It covers a valid token, missing scope, wrong audience, wrong issuer, invalid signature, and expiration.

### Client server

- `HomeControllerTest` constructs the controller explicitly and mocks only `ResourceServerClient`.
- `ResourceServerClientIntegrationTest` uses the production WebClient adapter against a controlled JDK HTTP server supplied by `ResourceServerClientIntegrationTestConfiguration`. The `ResourceServerStub` records requests and returns the response selected by each test, verifying the Bearer header and the existing `2xx`, `401`, `403`, and `5xx` rendering without a running resource server.

### Authorization server

`AuthServerIntegrationTest` starts the application on a random port with its production in-memory OAuth stores. It verifies:

- OIDC discovery and JWKS;
- M2M token issuance with the expected subject, audience, and scope;
- rejection of invalid M2M credentials;
- one active HTTP session per user, including expiration of the previous login.

## Integration, smoke, and end-to-end boundaries

A module integration test owns its in-process Spring/JVM test environment. Its Spring components and production adapters are real, while controlled stubs replace only external HTTP systems where needed. It does not contact Docker or an already-running application.

A smoke test does not own the runtime. It verifies a focused set of core black-box capabilities:

- authorization-server discovery and JWKS are available;
- the protected client page redirects to OAuth login;
- `/tasks` returns `401` without a Bearer token.

An end-to-end test treats the whole active stack as a black box. `E2E` describes that deployment boundary; `M2M` and `browser` identify two different actors and OAuth flows within it. The Cucumber M2M E2E suite uses the non-interactive `client_credentials` grant across the real authorization and resource servers, so it deliberately does not involve a browser, user, consent, client-server session or logout. The Playwright browser E2E suite uses authorization code with PKCE and crosses the browser plus all three applications to cover those interactive behaviors. Smoke is not a third E2E flow: it samples separate core endpoints without completing either cross-service journey.

The M2M E2E flow:

1. request a real `client_credentials` token;
2. inspect `iss`, `sub`, `aud`, and `scope`;
3. call `/tasks` with that token;
4. verify the subject and all three tasks;
5. verify that invalid client credentials are rejected.

System tests run in three explicit stages: preflight checks URL syntax and reachability, smoke verifies the core runtime capabilities, and E2E crosses the complete M2M flow. The E2E Gradle task depends on the earlier stages. Compose and k3d tasks pass public URLs and the advertised issuer as Gradle properties. No stage builds, starts, stops, seeds, or deletes either runtime; the configured OAuth clients and three tasks are already part of the applications.

GitHub Actions first compiles the system-test sources without a runtime. Its `System Tests (Docker Compose, JVM Images)` job then waits for every affected application workflow, builds and starts an ephemeral JVM Compose stack and exposes preflight, smoke, M2M E2E and real Playwright browser E2E as separate steps. The workflow owns that ephemeral lifecycle and always removes the stack; the Gradle and Playwright test tasks still never manage a runtime. Pull-request CI does not provision k3d.

The faster `Client Server Tests (No Docker) / Playwright Mock Tests (Mock Services, No Docker)` job remains inside the client workflow and depends only on Client integration tests. It runs the production Client executable while Node simulates the authorization and resource servers. Changes to Auth or Resource do not make that mock job validate either real application; their real browser integration is covered by `System Tests (Docker Compose, JVM Images)` instead.

`mise run ci:all` runs the same CI phases locally, including the three system-test stages through `ci:system`. It requires a Compose stack that is already running and neither starts nor stops it.

All three stages run on every request even when their source files are unchanged, because the external runtime may have changed since the previous execution.

The application integration tests avoid fixed-port conflicts: Spring, the JDK resource-server stub and the simulated OAuth server use dynamic ports. For black-box tests, the selected stack must already own ports 9000, 8080, and 8081. Preflight reports an unreachable process, while smoke catches a different process or runtime serving the wrong issuer or protocol response.

### Browser login, consent, tasks, and logout

The Playwright project is isolated under `playwright/` and pins Playwright 1.62.1. Install its npm dependencies and Chromium once with `mise run playwright:install`. Browser execution task names start with the selected runtime (`mock`, `compose`, or `k3d`), while `playwright:install` and `playwright:report` remain tool operations. Commands without `:ui` execute automatically in headless mode. Their matching `:ui` variants open the interactive Playwright test explorer and visible Chromium, but select the same profile and run the same three scenarios.

`mise run mock:playwright` and `mise run mock:playwright:ui` first build and then start the production `client-server` JAR on port 18080. Node-owned HTTP servers simulate only the external OIDC provider on port 19000 and resource server on port 19001. Playwright controls the mock stack, which stops its HTTP servers and client process when either task ends. The mock provider publishes discovery and JWKS metadata, validates the authorization request and S256 PKCE verifier, signs ID and access JWTs, and implements the registered OIDC logout redirect. The mock resource server verifies the JWT and returns `403` without `api.read` or the same three-task HTML returned by production with that scope. The three browser scenarios prove:

1. cancelling consent does not create a client login or expose tasks, and renders a safe page from which the user can retry;
2. submitting consent without `api.read` signs in but renders the real client's `403` representation instead of tasks;
3. granting `api.read` renders exactly Task 1, Task 2 and Task 3, refreshes the task area through the authenticated `/home/tasks` endpoint without reloading the page, then sends an ID token hint and registered post-logout redirect through `/connect/logout` before returning to login.

The refresh request uses the existing client-server session. The client server resolves its authorized OAuth client and calls the resource server itself, so the browser does not place the Bearer token in the refresh URL, headers or body.

The Compose and k3d commands, with or without `:ui`, never build, start, restart, seed or remove a runtime. Their identical preflight verifies the expected public issuer, client reachability and unauthenticated `/tasks` response before Chromium enters the demonstration credentials. The denial scenario uses a dedicated public PKCE client that is never approved, so previous consent cannot turn Cancel into a successful authorization. The missing-scope scenario uses the deployed `client-server` but omits `api.read` from that authorization request, proving that the real resource server returns `403` even if the user granted the scope during an earlier run. The positive scenario requests `api.read` and reaches the deployed resource server. Both deployed-client scenarios request but deliberately do not grant the optional `playwright.consent` marker, keeping the real consent page repeatable.

Browser automation uses the dedicated `playwright` / `playwright-pass` resource owner by default; `user` / `pass` remains the manual demonstration account. This separates authorization-server sessions and stored consent by principal, so a real browser test neither expires the human session nor changes the scopes shown later to the human user. `DCRIVELLA_PLAYWRIGHT_USERNAME` and `DCRIVELLA_PLAYWRIGHT_PASSWORD` may override the automation credentials when the selected runtime registers another dedicated test account.

The `/home` page intentionally renders complete tokens. Headless runs disable screenshots, video, traces and automatic failure-page snapshots. Playwright UI requires temporary traces and page snapshots for its interactive timeline; the three `:ui` tasks place those artifacts under a unique temporary directory and remove the directory when the explorer exits. While the explorer is open, its timeline and temporary files must still be treated as sensitive. Test output asserts only protocol structure and UI behavior and does not print token values.

### Why Cucumber is limited to E2E

Cucumber is used for `features/machine-to-machine.feature` because that flow has business-readable behavior shared across multiple HTTP systems: credentials produce a scoped token, and that token grants access to tasks. Its Given/When/Then steps execute the same real black-box requests described above.

Cucumber is not used for value objects, service unit tests, web slices, or adapter integrations. Those tests are clearer and faster as direct JUnit tests; wrapping technical assertions in feature steps would add indirection without making the behavior easier to discuss.

## JaCoCo coverage

JaCoCo 0.8.15 instruments all `Test` tasks in the three application modules. Each module's `jacocoTestReport` combines `test.exec` and `integrationTest.exec`, so the report reflects both fast tests and integration tests. Architecture and black-box system tests are not included because they do not measure production classes inside a single application module.

Generate all reports with:

```shell
mise run coverage
```

This command runs the unit/slice and integration suites without Docker. Reports are written to:

```text
auth-server/build/reports/jacoco/test/html/index.html
client-server/build/reports/jacoco/test/html/index.html
resource-server/build/reports/jacoco/test/html/index.html
```

XML reports are written beside each HTML report for future tooling. Coverage generation is opt-in and is not attached to `check` or `build`. No coverage threshold is configured yet: a gate should be chosen from an observed baseline and risk profile rather than introduced as an arbitrary percentage.

## Mutation testing

Regular tests check expected examples. JaCoCo checks whether production code ran. PIT asks whether those tests notice a behavior change: it temporarily reverses conditions, changes return values or removes calls, then reruns the tests covering the changed class.

- A **killed mutation** means a test detected the changed behavior.
- A **surviving mutation** may reveal a missing assertion or uncovered case. It can also be an equivalent mutation that does not change observable behavior, so survivors require review rather than automatic code changes.

Run the focused analysis with:

```shell
mise run test:mutation
```

PIT 1.25.9 and its JUnit 5 plugin analyze `HomeController`, the WebClient resource adapter, and the resource server's task, audience and presentation behavior. The analysis reuses unit tests, the MVC slice, the JDK HTTP stub and the mock-OAuth integration. Authorization-server configuration is not targeted yet because it has protocol-level integration coverage but no focused unit-test loop for individual configuration classes.

Mutation analysis is opt-in and does not belong to `test`, `check` or `build`. Module reports and an aggregate report are written under the corresponding `build/reports/pitest` directories. No score threshold is configured until the first report establishes which surviving mutations represent real test gaps.

## Current persistence boundary

The project intentionally has no database dependency or migration tool. Its learning scope is OAuth 2.0 and OpenID Connect behavior rather than persistence.

- Registered clients use `InMemoryRegisteredClientRepository`.
- Users use `InMemoryUserDetailsManager`.
- Authorizations and consents do not survive an authorization-server restart.
- Signing keys are generated in memory at startup.
- Resource-server tasks use `InMemoryTaskRepository`.

Compose and k3d therefore run only the three applications and do not create a database, migration schema or persistent application volume.

## Commands

```shell
mise run test
mise run test:integration
mise run test:all
mise run coverage
mise run test:mutation
mise run lint
mise run playwright:install
mise run mock:playwright
mise run mock:playwright:ui

# Require the corresponding runtime to be active already:
mise run compose:preflight
mise run compose:smoke
mise run compose:e2e
mise run compose:playwright
mise run compose:playwright:ui
mise run k3d:preflight
mise run k3d:smoke
mise run k3d:e2e
mise run k3d:playwright
mise run k3d:playwright:ui
```
