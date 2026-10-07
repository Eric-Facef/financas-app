package com.eric.financas.passkey;

import com.eric.financas.audit.AuditAction;
import com.eric.financas.audit.AuditService;
import com.eric.financas.common.exception.BusinessException;
import com.eric.financas.common.exception.NotFoundException;
import com.eric.financas.common.exception.UnauthorizedException;
import com.eric.financas.passkey.dto.PasskeyLoginRequest;
import com.eric.financas.passkey.dto.PasskeyOptionsResponse;
import com.eric.financas.passkey.dto.PasskeyRegisterRequest;
import com.eric.financas.passkey.dto.PasskeyResponse;
import com.eric.financas.user.User;
import com.eric.financas.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Login por digital/rosto/PIN do aparelho (WebAuthn).
 * <p>
 * Cadastro (usuário já logado): start -> o celular cria um par de chaves -> finish guarda a chave PÚBLICA.
 * Login: start -> o celular assina o desafio com a chave privada (após digital) -> finish confere a assinatura.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasskeyService {

    private static final int MAX_PASSKEYS_PER_USER = 10;

    private final RelyingParty rp;
    private final PasskeyRepository passkeys;
    private final UserRepository users;
    private final PasskeyChallengeStore challenges;
    private final AuditService audit;
    private final ObjectMapper mapper;

    /** Estado guardado entre o início e o fim do cadastro. */
    public record PendingRegistration(UUID userId, PublicKeyCredentialCreationOptions options) {
    }

    // ------------------------------------------------------------------ gerenciar

    @Transactional(readOnly = true)
    public List<PasskeyResponse> list(UUID userId) {
        return passkeys.findByUserIdOrderByCreatedAtDesc(userId).stream().map(PasskeyResponse::from).toList();
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Passkey passkey = passkeys.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Digital não encontrada"));
        passkeys.delete(passkey);
        audit.record(userId, AuditAction.PASSKEY_REMOVIDA, "Passkey", id.toString(), Map.of("nome", passkey.getName()));
    }

    // ------------------------------------------------------------------ cadastro

    public PasskeyOptionsResponse startRegistration(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        if (passkeys.countByUserId(userId) >= MAX_PASSKEYS_PER_USER) {
            throw new BusinessException("Limite de aparelhos cadastrados atingido");
        }

        PublicKeyCredentialCreationOptions options = rp.startRegistration(StartRegistrationOptions.builder()
                .user(UserIdentity.builder()
                        .name(user.getEmail())
                        .displayName(user.getEmail())
                        .id(UserHandles.fromUserId(userId))
                        .build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)          // "passkey": permite entrar sem digitar e-mail
                        .userVerification(UserVerificationRequirement.REQUIRED) // exige digital/rosto/PIN
                        .build())
                .build());

        String json;
        try {
            json = options.toCredentialsCreateJson();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar as opções de cadastro da digital", e);
        }
        return new PasskeyOptionsResponse(challenges.put(new PendingRegistration(userId, options)), toNode(json));
    }

    @Transactional
    public PasskeyResponse finishRegistration(UUID userId, PasskeyRegisterRequest req) {
        PendingRegistration pending = challenges.take(req.challengeId(), PendingRegistration.class);
        if (!pending.userId().equals(userId)) {
            throw new UnauthorizedException("Desafio inválido ou expirado. Tente novamente.");
        }

        RegistrationResult result;
        try {
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> credential =
                    PublicKeyCredential.parseRegistrationResponseJson(req.credential().toString());
            result = rp.finishRegistration(FinishRegistrationOptions.builder()
                    .request(pending.options())
                    .response(credential)
                    .build());
        } catch (Exception e) {
            log.warn("Falha ao validar o cadastro da passkey: {}", e.getMessage());
            throw new BusinessException("Não foi possível validar a digital neste aparelho");
        }

        String name = req.name() == null || req.name().isBlank() ? "Meu aparelho" : req.name().trim();
        Passkey saved = passkeys.save(new Passkey(
                userId,
                result.getKeyId().getId().getBase64Url(),
                result.getPublicKeyCose().getBase64Url(),
                result.getSignatureCount(),
                name));

        audit.record(userId, AuditAction.PASSKEY_CRIADA, "Passkey", saved.getId().toString(), Map.of("nome", name));
        return PasskeyResponse.from(saved);
    }

    // ------------------------------------------------------------------ login

    public PasskeyOptionsResponse startLogin() {
        // Sem informar usuário: o celular mostra as passkeys dele e quem decide é o "user handle" salvo nela.
        AssertionRequest request = rp.startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build());

        String json;
        try {
            json = request.toCredentialsGetJson();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar as opções de login por digital", e);
        }
        return new PasskeyOptionsResponse(challenges.put(request), toNode(json));
    }

    /**
     * noRollbackFor: a falha também precisa "commitar" para o evento de auditoria (pós-commit) ser gravado.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public User finishLogin(PasskeyLoginRequest req) {
        AssertionRequest request = challenges.take(req.challengeId(), AssertionRequest.class);
        AssertionResult result = verifyAssertion(request, req);

        Passkey passkey = passkeys.findByCredentialId(result.getCredentialId().getBase64Url())
                .orElseThrow(() -> new UnauthorizedException("Digital não reconhecida"));
        // O contador de assinaturas ajuda a detectar credenciais clonadas
        passkey.setSignatureCount(result.getSignatureCount());
        passkey.setLastUsedAt(Instant.now());

        return users.findById(passkey.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));
    }

    private AssertionResult verifyAssertion(AssertionRequest request, PasskeyLoginRequest req) {
        try {
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> credential =
                    PublicKeyCredential.parseAssertionResponseJson(req.credential().toString());
            AssertionResult result = rp.finishAssertion(FinishAssertionOptions.builder()
                    .request(request)
                    .response(credential)
                    .build());
            if (result.isSuccess()) {
                return result;
            }
        } catch (Exception e) {
            log.warn("Falha ao validar o login por passkey: {}", e.getMessage());
        }
        audit.record(null, AuditAction.LOGIN_FALHA, "User", null, Map.of("metodo", "passkey"));
        throw new UnauthorizedException("Não foi possível entrar com a digital");
    }

    private JsonNode toNode(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("JSON de passkey inválido", e);
        }
    }
}
