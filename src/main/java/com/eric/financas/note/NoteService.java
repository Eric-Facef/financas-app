package com.eric.financas.note;

import com.eric.financas.common.exception.BusinessException;
import com.eric.financas.common.exception.NotFoundException;
import com.eric.financas.note.dto.NoteRequest;
import com.eric.financas.note.dto.NoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteService {

    /** Teto por usuário: evita crescimento sem limite numa tabela de rascunhos. */
    static final int MAX_NOTES = 200;

    private final NoteRepository notes;

    @Transactional(readOnly = true)
    public List<NoteResponse> list(UUID userId) {
        return notes.findByUserIdOrderByDoneAscCreatedAtDesc(userId).stream().map(NoteResponse::from).toList();
    }

    @Transactional
    public NoteResponse create(UUID userId, NoteRequest req) {
        if (notes.countByUserId(userId) >= MAX_NOTES) {
            throw new BusinessException("Limite de " + MAX_NOTES + " notas atingido. Apague as feitas para continuar.");
        }
        Note note = notes.save(new Note(userId, req.text().trim(), Boolean.TRUE.equals(req.done())));
        return NoteResponse.from(note);
    }

    @Transactional
    public NoteResponse update(UUID userId, UUID id, NoteRequest req) {
        Note note = findOwned(userId, id);
        note.setContent(req.text().trim());
        if (req.done() != null) {
            note.setDone(req.done());
        }
        return NoteResponse.from(note);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        notes.delete(findOwned(userId, id));
    }

    /** Apaga de uma vez todas as notas marcadas como feitas. */
    @Transactional
    public int deleteDone(UUID userId) {
        return notes.deleteDone(userId);
    }

    private Note findOwned(UUID userId, UUID id) {
        return notes.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Nota não encontrada"));
    }
}
