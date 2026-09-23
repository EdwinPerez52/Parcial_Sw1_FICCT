package com.collabmodeler.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Explicit, temporary bootstrap for demonstration environments. It is disabled
 * unless APP_DEMO_ACCOUNTS is supplied and never loads in the prod profile.
 * Format: email|password;email|password. The first account is an administrator.
 */
@Component
@Profile("dev")
class DemoAccountInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoAccountInitializer.class);
    private final AuthStore store;
    private final PasswordEncoder passwords;
    private final String configuredAccounts;

    DemoAccountInitializer(AuthStore store, PasswordEncoder passwords,
                           @Value("${app.demo-accounts:}") String configuredAccounts) {
        this.store = store;
        this.passwords = passwords;
        this.configuredAccounts = configuredAccounts;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (configuredAccounts == null || configuredAccounts.isBlank()) return;
        int position = 0;
        for (String raw : configuredAccounts.split(";")) {
            String[] parts = raw.trim().split("\\|", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException("APP_DEMO_ACCOUNTS debe usar email|password;email|password");
            }
            String email = parts[0].trim().toLowerCase(Locale.ROOT);
            String password = parts[1].trim();
            int accountPosition = position;
            AuthStore.Account account = store.accountByEmail(email).orElseGet(() -> {
                AuthStore.Account created = store.createAccount(email, "Demo user " + (accountPosition + 1), true);
                store.createIdentity(created.id(), "LOCAL", email, passwords.encode(password));
                return created;
            });
            store.verify(account.id());
            store.updatePassword(account.id(), passwords.encode(password));
            if (position == 0) store.makeAdmin(account.id());
            position++;
        }
        log.warn("Temporary demo accounts enabled. Remove APP_DEMO_ACCOUNTS after the demonstration.");
    }
}
