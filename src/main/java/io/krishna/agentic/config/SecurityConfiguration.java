package io.krishna.agentic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    UserDetailsService users(@Value("${platform.operator-password}") String operatorPassword,
            @Value("${platform.approver-password}") String approverPassword) {
        if (operatorPassword.length() < 12 || approverPassword.length() < 12 || operatorPassword.equals(approverPassword)) {
            throw new IllegalArgumentException("Use distinct operator and approver passwords of at least 12 characters");
        }
        var encoder = new BCryptPasswordEncoder();
        return new InMemoryUserDetailsManager(
                User.withUsername("operator").password(encoder.encode(operatorPassword)).roles("OPERATOR").build(),
                User.withUsername("approver").password(encoder.encode(approverPassword)).roles("APPROVER").build());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows/*/approvals").hasRole("APPROVER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows", "/api/v1/workflows/**").hasRole("OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/workflows/**", "/actuator/prometheus").hasAnyRole("OPERATOR", "APPROVER")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults()).build();
    }
}
