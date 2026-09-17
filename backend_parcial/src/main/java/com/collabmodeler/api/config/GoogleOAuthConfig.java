package com.collabmodeler.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration
public class GoogleOAuthConfig {
    @Bean
    @Conditional(GoogleCredentialsPresent.class)
    ClientRegistrationRepository googleClients(Environment environment) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
            .clientId(environment.getRequiredProperty("GOOGLE_CLIENT_ID"))
            .clientSecret(environment.getRequiredProperty("GOOGLE_CLIENT_SECRET"))
            .scope("openid", "profile", "email").build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
