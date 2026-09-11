package io.krishna.agentic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecurityConfigurationTest {
    @Test
    void createsDelegatingEncoderCompatibleUsers() {
        var users = new SecurityConfiguration().users("operator-password-12", "approver-password-12");
        assertThat(users.loadUserByUsername("operator").getPassword()).startsWith("{bcrypt}$2");
        assertThat(users.loadUserByUsername("approver").getPassword()).startsWith("{bcrypt}$2");
    }

    @Test
    void rejectsWeakOrSharedPasswords() {
        var configuration = new SecurityConfiguration();
        assertThatThrownBy(() -> configuration.users("short", "another-short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration.users("same-password-12", "same-password-12"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
