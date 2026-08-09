package cloud.dcrivella.resourceserver.presentation.task;

import cloud.dcrivella.resourceserver.application.task.ListTasks;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Protected HTTP controller that renders tasks for the authenticated token subject.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@RestController
@RequestMapping("/tasks")
public class TaskRestController {

    private final ListTasks listTasks;

    /**
     * Creates the controller with the task listing application service.
     *
     * @param listTasks task listing service
     */
    public TaskRestController(ListTasks listTasks) {
        this.listTasks = listTasks;
    }

    /**
     * Renders every task for a bearer token granted the {@code api.read} scope.
     *
     * @param jwt validated access token representing the caller
     * @return the existing HTML task representation
     */
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_api.read')")
    public String getTasks(@AuthenticationPrincipal Jwt jwt) {
        String items = listTasks.execute().stream().map(task -> "    <li>%s</li>".formatted(task.title()))
                .collect(Collectors.joining("\n"));

        return """
                <h1>Tasks for %s:</h1>

                <ol>
                %s
                </ol>
                """.formatted(jwt.getSubject(), items);
    }
}
