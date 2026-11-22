package br.com.wedding.api.config;

import static java.lang.Math.toIntExact;
import static org.springframework.boot.convert.DurationStyle.detectAndParse;

import java.util.List;

import br.com.wedding.api.admin.ActiveAdminSessionFilter;
import br.com.wedding.api.admin.AdminAccountRepository;
import br.com.wedding.api.invite.access.GuestAccessProperties;
import br.com.wedding.api.invite.access.GuestAccessService;
import br.com.wedding.api.invite.access.GuestLoginAttemptService;
import br.com.wedding.api.invite.access.GuestLoginFilter;
import br.com.wedding.api.invite.access.TurnstileVerifier;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private final String[] allowedOrigins;
    private final int sessionTimeoutSeconds;

    public SecurityConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins,
            @Value("${spring.session.timeout}") String sessionTimeout) {
        this.allowedOrigins = allowedOrigins;
        this.sessionTimeoutSeconds = toIntExact(detectAndParse(sessionTimeout).toSeconds());
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    public SessionAuthenticationStrategy guestSessionAuthenticationStrategy(CsrfTokenRepository tokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(tokens)));
    }

    @Bean
    public GuestLoginFilter guestLoginFilter(GuestAccessService access, GuestLoginAttemptService attempts,
            GuestAccessProperties properties, JsonMapper json, SecurityContextRepository contexts,
            SessionAuthenticationStrategy guestSessionAuthenticationStrategy, TurnstileVerifier turnstile) {
        GuestLoginFilter filter = new GuestLoginFilter(
                authentication -> access.authenticate((String) authentication.getCredentials()),
                attempts, properties, json, turnstile);
        filter.setSessionAuthenticationStrategy(guestSessionAuthenticationStrategy);
        filter.setSecurityContextRepository(contexts);
        return filter;
    }

    @Bean
    public FilterRegistrationBean<GuestLoginFilter> guestLoginFilterRegistration(GuestLoginFilter filter) {
        FilterRegistrationBean<GuestLoginFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AdminAccountRepository accounts,
            SecurityContextRepository contexts, CsrfTokenRepository tokens, GuestLoginFilter guestLoginFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.csrfTokenRepository(tokens))
                .securityContext(context -> context.securityContextRepository(contexts))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/hello-world", "/actuator/health").permitAll()
                        .requestMatchers("/api/auth/csrf", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/guest/login").permitAll()
                        .requestMatchers("/api/auth/me", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/guest/**").hasRole("GUEST")
                        .anyRequest().denyAll())
                .formLogin(login -> login
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) -> {
                            request.getSession().setMaxInactiveInterval(sessionTimeoutSeconds);
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        })
                        .failureHandler((request, response, exception) ->
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .deleteCookies("SESSION")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN)))
                .addFilterBefore(new ActiveAdminSessionFilter(accounts), AuthorizationFilter.class)
                .addFilterBefore(guestLoginFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
