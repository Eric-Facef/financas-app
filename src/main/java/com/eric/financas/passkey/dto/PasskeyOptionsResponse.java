package com.eric.financas.passkey.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** challengeId identifica o desafio no servidor; options vai direto para navigator.credentials.create/get. */
public record PasskeyOptionsResponse(String challengeId, JsonNode options) {
}
