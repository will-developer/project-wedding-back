package br.com.wedding.api;

import static java.util.Arrays.asList;

import java.io.Console;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.com.wedding.api.admin.AdminAccountSetup;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class WeddingApiApplication {

    public static void main(String[] args) {
        if (asList(args).contains("--create-admin")) {
            runAdminSetup(args);
            return;
        }
        SpringApplication.run(WeddingApiApplication.class, args);
    }

    private static void runAdminSetup(String[] args) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Run java -jar with --create-admin in an interactive terminal.");
        }

        SpringApplication application = new SpringApplication(WeddingApiApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("admin-setup");

        try (ConfigurableApplicationContext context = application.run(adminSetupArguments(args))) {
            context.getBean(AdminAccountSetup.class).run(console);
        }
    }

    static String[] adminSetupArguments(String[] args) {
        Map<String, String> requiredSettings = Map.of(
                "spring.main.web-application-type", "none",
                "spring.flyway.enabled", "false",
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.sql.init.mode", "never");
        List<String> arguments = new ArrayList<>(asList(args));

        // Account creation must not start HTTP or execute schema initialization.
        for (Map.Entry<String, String> setting : requiredSettings.entrySet()) {
            String option = "--" + setting.getKey();
            arguments.removeIf(argument -> argument.equals(option) || argument.startsWith(option + "="));
            arguments.add(option + "=" + setting.getValue());
        }
        return arguments.toArray(new String[0]);
    }

}
