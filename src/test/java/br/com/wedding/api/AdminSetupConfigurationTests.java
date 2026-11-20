package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;

class AdminSetupConfigurationTests {

    @Test
    void setupOverridesUnsafeSettingsAndPreservesDatabaseSelection() {
        String[] arguments = WeddingApiApplication.adminSetupArguments(new String[] {
                "--create-admin",
                "--spring.flyway.enabled=true",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.main.web-application-type=servlet",
                "--spring.sql.init.mode=always",
                "--spring.datasource.url=jdbc:postgresql://localhost:5432/test"
        });
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("externalSettings", Map.of(
                "spring.flyway.enabled", "true",
                "spring.jpa.hibernate.ddl-auto", "create-drop")));
        environment.getPropertySources().addFirst(new SimpleCommandLinePropertySource(arguments));

        assertThat(environment.getProperty("spring.main.web-application-type")).isEqualTo("none");
        assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/test");
    }
}
