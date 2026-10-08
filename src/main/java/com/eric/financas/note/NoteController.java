package com.eric.financas.note;

import com.eric.financas.common.security.AuthenticatedUser;
import com.eric.financas.note.dto.NoteRequest;
import com.eric.financas.note.dto.NoteResponse;
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

@Tag(name = "Notas")
@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService service;

    @GetMapping
    public List<NoteResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody NoteRequest request) {
        return service.create(user.id(), request);
    }

    @PutMapping("/{id}")
    public NoteResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable UUID id,
                               @Valid @RequestBody NoteRequest request) {
        return service.update(user.id(), id, request);
    }

    /** Literal "/done" tem prioridade sobre "/{id}". */
    @DeleteMapping("/done")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDone(@AuthenticationPrincipal AuthenticatedUser user) {
        service.deleteDone(user.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.delete(user.id(), id);
    }
}
