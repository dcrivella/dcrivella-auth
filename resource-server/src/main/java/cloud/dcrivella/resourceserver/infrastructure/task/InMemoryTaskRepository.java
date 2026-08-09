package cloud.dcrivella.resourceserver.infrastructure.task;

import cloud.dcrivella.resourceserver.domain.task.Task;
import cloud.dcrivella.resourceserver.domain.task.TaskRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * In-memory task repository adapter that preserves the three demonstration tasks.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Repository
public class InMemoryTaskRepository implements TaskRepository {

    private static final List<Task> TASKS = List.of(new Task("Task 1"), new Task("Task 2"), new Task("Task 3"));

    /**
     * Returns the fixed task collection in display order.
     *
     * @return the three demonstration tasks
     */
    @Override
    public List<Task> findAll() {
        return TASKS;
    }
}
