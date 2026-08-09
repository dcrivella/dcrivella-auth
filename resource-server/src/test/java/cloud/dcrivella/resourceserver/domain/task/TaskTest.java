package cloud.dcrivella.resourceserver.domain.task;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * Tests the task title invariant.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
class TaskTest {

    /** Verifies that a task cannot be created with a blank title. */
    @Test
    void rejectsBlankTitles() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(" ")).withMessage("title must not be blank");
    }
}
