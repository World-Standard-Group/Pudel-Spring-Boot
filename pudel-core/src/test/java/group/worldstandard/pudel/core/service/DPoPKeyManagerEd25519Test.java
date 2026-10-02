/* Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package group.worldstandard.pudel.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import group.worldstandard.pudel.core.entity.DPoPKey;
import group.worldstandard.pudel.core.repository.DPoPKeyRepository;

class DPoPKeyManagerEd25519Test {

    private DPoPKeyRepository repository;
    private DPoPKeyManager manager;
    private DPoPService dpopService;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(DPoPKeyRepository.class);
        manager = new DPoPKeyManager(repository, new tools.jackson.databind.ObjectMapper());
        ReflectionTestUtils.setField(manager, "jwtExpirationMillis", 604800000L);
        ReflectionTestUtils.setField(manager, "maxKeysPerUser", 10);
        dpopService = new DPoPService(manager);
    }

    @Test
    void generatesOkpEd25519KeyAndSignsVerifiableProof() {
        when(repository.save(any(DPoPKey.class))).thenAnswer(invocation -> {
            DPoPKey saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(repository.countByUserIdAndIsActiveTrue("anonymous")).thenReturn(0L);

        DPoPKeyManager.DPoPKeyInfo info = manager.generateAndStoreKeyPair("anonymous", null);
        DPoPKey captured = capturedKey();
        when(repository.findByKeyId(info.getKeyId())).thenReturn(Optional.of(captured));

        assertTrue(captured.getPublicKeyJwk().contains("\"kty\":\"OKP\""));
        assertTrue(captured.getPublicKeyJwk().contains("\"crv\":\"Ed25519\""));

        long nowSeconds = java.time.Instant.now().getEpochSecond();
        String proof = manager.signDPoPProof(
                "{\"jti\":\"unit-jti\",\"htm\":\"GET\",\"htu\":\"https://unit.invalid/api/test\",\"iat\":" + nowSeconds + "}",
                info.getKeyId());

        var result = dpopService.validateProofForResource(
                proof, "GET", "https://unit.invalid/api/test", null, info.getKeyId());
        assertTrue(result.valid(), () -> "proof rejected: " + result.error());
        assertEquals(captured.getPublicKeyThumbprint(), result.thumbprint());
    }

    private DPoPKey capturedKey() {
        org.mockito.ArgumentCaptor<DPoPKey> captor =
                org.mockito.ArgumentCaptor.forClass(DPoPKey.class);
        Mockito.verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
