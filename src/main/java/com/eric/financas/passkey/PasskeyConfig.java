package com.eric.financas.passkey;

import com.eric.financas.common.config.AppProperties;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;

@Configuration
public class PasskeyConfig {

    @Bean
    RelyingParty relyingParty(PasskeyProperties props, AppProperties app, CredentialRepository credentials) {
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id(props.rpId()).name(props.rpName()).build())
                .credentialRepository(credentials)
                // Origens (https://dominio[:porta]) autorizadas a usar a passkey: as mesmas do CORS
                .origins(new HashSet<>(app.allowedOrigins()))
                .build();
    }
}
