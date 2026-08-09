package cloud.dcrivella.resourceserver.domain.task;

/**
 * A task exposed by the protected resource server.
 *
 * @param title non-blank task title
 * @author Douglas Crivella
 * @created August 8, 2026
 */
public record Task(String title) {

    /**
     * Enforces the task title invariant.
     *
     * @throws IllegalArgumentException when the title is null or blank
     */
    public Task {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
    }
}
