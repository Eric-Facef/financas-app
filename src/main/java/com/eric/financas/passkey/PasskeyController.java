package com.eric.financas.passkey;

import com.eric.financas.common.security.AuthenticatedUser;
import com.eric.financas.passkey.dto.PasskeyOptionsResponse;
import com.eric.financas.passkey.dto.PasskeyRegisterRequest;
import com.eric.financas.passkey.dto.PasskeyResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Gerenciamento das digitais do usuário logado. (O LOGIN por digital fica em /api/v1/auth/passkey/*.) */
@Tag(name = "Passkeys")
@RestController
@RequestMapping("/api/v1/passkeys")
@RequiredArgsConstructor
public class PasskeyController {

    private final PasskeyService service;

    @GetMapping
    public List<PasskeyResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.id());
    }

    @PostMapping("/register/options")
    public PasskeyOptionsResponse registerOptions(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.startRegistration(user.id());
    }

    @PostMapping("/register/verify")
    @ResponseStatus(HttpStatus.CREATED)
    public PasskeyResponse registerVerify(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody PasskeyRegisterRequest request) {
        return service.finishRegistration(user.id(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.delete(user.id(), id);
    }
}
