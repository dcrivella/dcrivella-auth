package cloud.dcrivella.resourceserver.domain.task;

import java.util.List;

/**
 * Domain port for retrieving the tasks exposed by the resource server.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
public interface TaskRepository {

    /**
     * Retrieves every available task in display order.
     *
     * @return the available tasks
     */
    List<Task> findAll();
}
