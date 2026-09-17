package com.collabmodeler.api.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class GoogleCredentialsPresent implements Condition {
    @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String clientId = context.getEnvironment().getProperty("GOOGLE_CLIENT_ID", "");
        String secret = context.getEnvironment().getProperty("GOOGLE_CLIENT_SECRET", "");
        return !clientId.isBlank() && !secret.isBlank();
    }
}
