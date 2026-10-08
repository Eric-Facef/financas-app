package com.eric.financas.note.dto;

import com.eric.financas.note.Note;

import java.time.Instant;
import java.util.UUID;

public record NoteResponse(UUID id, String text, boolean done, Instant createdAt) {

    public static NoteResponse from(Note note) {
        return new NoteResponse(note.getId(), note.getContent(), note.isDone(), note.getCreatedAt());
    }
}
