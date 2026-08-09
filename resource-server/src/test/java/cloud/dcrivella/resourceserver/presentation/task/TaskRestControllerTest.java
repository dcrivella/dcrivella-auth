package cloud.dcrivella.resourceserver.presentation.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import cloud.dcrivella.resourceserver.application.task.ListTasks;
import cloud.dcrivella.resourceserver.domain.task.Task;
import cloud.dcrivella.resourceserver.infrastructure.security.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests task MVC mapping and production security while mocking only the application service.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@WebMvcTest(TaskRestController.class)
@Import(SecurityConfig.class)
@MockitoBean(types = {ListTasks.class, JwtDecoder.class})
class TaskRestControllerTest {

    private final MockMvc mockMvc;
    private final ListTasks listTasks;

    /**
     * Creates the MVC slice test with its HTTP client and mocked application service.
     *
     * @param mockMvc HTTP test client connected to the MVC slice
     * @param listTasks mocked application service registered by {@link MockitoBean}
     */
    TaskRestControllerTest(MockMvc mockMvc, ListTasks listTasks) {
        this.mockMvc = mockMvc;
        this.listTasks = listTasks;
    }

    /** Verifies that the task endpoint rejects a request without a bearer token. */
    @Test
    void rejectsRequestsWithoutABearerToken() throws Exception {
        var response = mockMvc.perform(get("/tasks")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    /** Verifies that an authenticated caller without {@code api.read} receives a forbidden response. */
    @Test
    void rejectsAuthenticatedRequestsWithoutTheRequiredScope() throws Exception {
        var response = mockMvc.perform(get("/tasks").with(jwt().jwt(token -> token.subject("subject-123")))).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    /** Verifies that an authorized caller receives every task from the application service. */
    @Test
    void rendersTasksForAUserWithTheRequiredScope() throws Exception {
        // Given
        given(listTasks.execute()).willReturn(List.of(new Task("Task 1"), new Task("Task 2"), new Task("Task 3")));

        // When/Then
        var response = mockMvc
                .perform(get("/tasks")
                        .with(jwt().jwt(token -> token.subject("subject-123")).authorities(new SimpleGrantedAuthority("SCOPE_api.read"))))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).contains("<h1>Tasks for subject-123:</h1>", "<li>Task 1</li>", "<li>Task 2</li>",
                "<li>Task 3</li>");
    }
}
