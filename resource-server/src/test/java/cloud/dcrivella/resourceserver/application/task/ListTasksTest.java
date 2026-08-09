package cloud.dcrivella.resourceserver.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import cloud.dcrivella.resourceserver.domain.task.Task;
import cloud.dcrivella.resourceserver.domain.task.TaskRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the task listing service with its repository port isolated by Mockito.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@ExtendWith(MockitoExtension.class)
class ListTasksTest {

    @Mock
    private TaskRepository taskRepository;

    private ListTasks listTasks;

    /** Creates the service explicitly with its mocked repository before each test. */
    @BeforeEach
    void setUp() {
        listTasks = new ListTasks(taskRepository);
    }

    /** Verifies that the service returns the tasks supplied by its repository port. */
    @Test
    void returnsTasksProvidedByTheRepositoryPort() {
        // Given
        List<Task> tasks = List.of(new Task("Repository task"));
        given(taskRepository.findAll()).willReturn(tasks);

        // When
        List<Task> result = listTasks.execute();

        // Then
        assertThat(result).isSameAs(tasks);
        verify(taskRepository).findAll();
    }
}
