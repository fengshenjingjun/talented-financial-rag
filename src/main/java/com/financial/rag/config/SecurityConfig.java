package com.financial.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> {});

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // Production: replace with LDAP/OAuth2 integration
        var analyst = User.withDefaultPasswordEncoder()
                .username("analyst")
                .password("analyst123")
                .roles("ANALYST")
                .build();

        var admin = User.withDefaultPasswordEncoder()
                .username("admin")
                .password("admin123")
                .roles("ADMIN", "ANALYST")
                .build();

        return new InMemoryUserDetailsManager(analyst, admin);
    }
}
