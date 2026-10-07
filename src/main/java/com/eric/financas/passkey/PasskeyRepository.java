package com.eric.financas.passkey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasskeyRepository extends JpaRepository<Passkey, UUID> {

    List<Passkey> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Passkey> findByCredentialId(String credentialId);

    Optional<Passkey> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
