package com.eric.financas.account;

import com.eric.financas.account.dto.AccountRequest;
import com.eric.financas.account.dto.AccountResponse;
import com.eric.financas.account.dto.AccountUpdateRequest;
import com.eric.financas.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Contas")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService service;

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody AccountRequest request) {
        return service.create(user.id(), request);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody AccountUpdateRequest request) {
        return service.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.delete(user.id(), id);
    }
}
