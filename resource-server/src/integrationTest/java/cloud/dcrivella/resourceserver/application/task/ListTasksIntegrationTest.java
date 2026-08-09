package cloud.dcrivella.resourceserver.application.task;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.dcrivella.resourceserver.domain.task.Task;
import cloud.dcrivella.resourceserver.infrastructure.task.InMemoryTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Verifies the task listing service composed with the production in-memory repository adapter.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@SpringJUnitConfig({ListTasks.class, InMemoryTaskRepository.class})
class ListTasksIntegrationTest {

    private final ListTasks listTasks;

    /**
     * Creates the integration test with the service composed by Spring.
     *
     * @param listTasks task service connected to the production in-memory adapter
     */
    ListTasksIntegrationTest(ListTasks listTasks) {
        this.listTasks = listTasks;
    }

    /** Verifies that the real adapter preserves all three demonstration tasks and their order. */
    @Test
    void listsTasksThroughTheRealInMemoryAdapter() {
        assertThat(listTasks.execute()).extracting(Task::title).containsExactly("Task 1", "Task 2", "Task 3");
    }
}
