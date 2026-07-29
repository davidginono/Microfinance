package com.sacco.mvp.security;

import org.junit.jupiter.api.Test;

import java.io.ObjectStreamClass;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserPrincipalTest {
    @Test
    void serialVersionUidRemainsCompatibleWithExistingSessions() {
        assertThat(ObjectStreamClass.lookup(AppUserPrincipal.class).getSerialVersionUID())
            .isEqualTo(-4042803929349064595L);
    }
}
