package cloud.dcrivella.resourceserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestSecurityConfig.class)
class ResourceServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
