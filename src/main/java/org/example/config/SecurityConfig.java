package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/index.html", "/jev.webp", "/login", "/login.html", "/error").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/jev", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll())
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
