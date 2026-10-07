package com.eric.financas.passkey;

import com.eric.financas.user.User;
import com.eric.financas.user.UserRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ponte entre a biblioteca da Yubico e o nosso banco: ela pergunta, nós respondemos com o que está na tabela passkeys.
 */
@Component
@RequiredArgsConstructor
public class PasskeyCredentialRepository implements CredentialRepository {

    private final PasskeyRepository passkeys;
    private final UserRepository users;

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return users.findByEmail(username)
                .map(user -> passkeys.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                        .map(p -> PublicKeyCredentialDescriptor.builder().id(decode(p.getCredentialId())).build())
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return users.findByEmail(username).map(user -> UserHandles.fromUserId(user.getId()));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return UserHandles.toUserId(userHandle).flatMap(users::findById).map(User::getEmail);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return passkeys.findByCredentialId(credentialId.getBase64Url())
                .filter(p -> UserHandles.toUserId(userHandle).map(id -> id.equals(p.getUserId())).orElse(false))
                .map(this::toRegistered);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return passkeys.findByCredentialId(credentialId.getBase64Url())
                .map(p -> Set.of(toRegistered(p)))
                .orElse(Set.of());
    }

    private RegisteredCredential toRegistered(Passkey p) {
        return RegisteredCredential.builder()
                .credentialId(decode(p.getCredentialId()))
                .userHandle(UserHandles.fromUserId(p.getUserId()))
                .publicKeyCose(decode(p.getPublicKeyCose()))
                .signatureCount(p.getSignatureCount())
                .build();
    }

    private static ByteArray decode(String base64Url) {
        try {
            return ByteArray.fromBase64Url(base64Url);
        } catch (Exception e) {
            throw new IllegalStateException("Credencial com base64url inválido no banco", e);
        }
    }
}
