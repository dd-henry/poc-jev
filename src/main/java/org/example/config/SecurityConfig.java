package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST,
                                "/api/triage",
                                "/api/triage-batch",
                                "/api/triage/ingest",
                                "/api/newsletter/script").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/newsletter/script").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/history").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/#projetos", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**")))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    InMemoryUserDetailsManager userDetailsService(
            @Value("${jev.access-password}") String accessPassword) {
        if (accessPassword == null || accessPassword.isBlank()) {
            throw new IllegalStateException("JEV_ACCESS_PASSWORD must be configured.");
        }
        UserDetails user = User.withUsername("friends")
                .password("{noop}" + accessPassword)
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}
