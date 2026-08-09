package cloud.dcrivella.resourceserver.application.task;

import cloud.dcrivella.resourceserver.domain.task.Task;
import cloud.dcrivella.resourceserver.domain.task.TaskRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Application service that lists tasks through the task repository port.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Service
public class ListTasks {

    private final TaskRepository taskRepository;

    /**
     * Creates the service with its task repository port.
     *
     * @param taskRepository task retrieval port
     */
    public ListTasks(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * Lists every available task.
     *
     * @return the tasks supplied by the repository port
     */
    public List<Task> execute() {
        return taskRepository.findAll();
    }
}
