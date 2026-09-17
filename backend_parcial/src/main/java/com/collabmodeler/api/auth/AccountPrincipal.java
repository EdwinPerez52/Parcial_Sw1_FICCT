package com.collabmodeler.api.auth;

import java.security.Principal;
import java.util.UUID;

public record AccountPrincipal(UUID accountId, String email, String fullName,
                               boolean verified, boolean platformAdmin) implements Principal {
    @Override public String getName() { return "account:" + accountId; }
}
