package br.com.wedding.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
class WeddingApiApplicationTests {

    @Test
    void contextLoads() {
    }

}
