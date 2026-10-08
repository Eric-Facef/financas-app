package com.eric.financas.note;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    /** Pendentes primeiro (mais novas no topo), depois as feitas. */
    List<Note> findByUserIdOrderByDoneAscCreatedAtDesc(UUID userId);

    Optional<Note> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Modifying
    @Query("delete from Note n where n.userId = :userId and n.done = true")
    int deleteDone(@Param("userId") UUID userId);
}
