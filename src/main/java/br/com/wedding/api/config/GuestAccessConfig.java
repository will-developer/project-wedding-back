package br.com.wedding.api.config;

import br.com.wedding.api.invite.access.GuestAccessProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

@Configuration
@EnableConfigurationProperties(GuestAccessProperties.class)
@EnableResilientMethods
public class GuestAccessConfig {
}
