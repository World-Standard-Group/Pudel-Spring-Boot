/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed with an additional permission known as the
 * "Pudel Plugin Exception".
 *
 * See the LICENSE and PLUGIN_EXCEPTION files in the project root for details.
 */
package group.worldstandard.pudel.core.repository;

import group.worldstandard.pudel.core.entity.DPoPKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for DPoP key persistence operations.
 */
@Repository
public interface DPoPKeyRepository extends JpaRepository<DPoPKey, Long> {

    /**
     * Find a DPoP key by its unique key identifier.
     */
    Optional<DPoPKey> findByKeyId(String keyId);

    /**
     * Find all active DPoP keys for a user.
     */
    List<DPoPKey> findByUserIdAndIsActiveTrue(String userId);

    /**
     * Find a DPoP key by user ID and token thumbprint.
     * Used to retrieve the key bound to a specific access token.
     */
    Optional<DPoPKey> findByUserIdAndTokenThumbprintAndIsActiveTrue(String userId, String tokenThumbprint);

    /**
     * Find a DPoP key by its public key thumbprint.
     */
    Optional<DPoPKey> findByPublicKeyThumbprintAndIsActiveTrue(String publicKeyThumbprint);

    /**
     * Deactivate a DPoP key by its key ID.
     */
    @Modifying
    @Query("UPDATE DPoPKey d SET d.isActive = false WHERE d.keyId = :keyId")
    int deactivateByKeyId(@Param("keyId") String keyId);

    /**
     * Deactivate all DPoP keys for a user.
     */
    @Modifying
    @Query("UPDATE DPoPKey d SET d.isActive = false WHERE d.userId = :userId")
    int deactivateAllByUserId(@Param("userId") String userId);

    /**
     * Deactivate all DPoP keys bound to a specific token thumbprint.
     */
    @Modifying
    @Query("UPDATE DPoPKey d SET d.isActive = false WHERE d.tokenThumbprint = :tokenThumbprint")
    int deactivateByTokenThumbprint(@Param("tokenThumbprint") String tokenThumbprint);

    /**
     * Delete expired DPoP keys (for cleanup).
     */
    @Modifying
    @Query("DELETE FROM DPoPKey d WHERE d.expiresAt < :now")
    int deleteExpiredKeys(@Param("now") Instant now);

    /**
     * Delete inactive DPoP keys older than the specified date.
     */
    @Modifying
    @Query("DELETE FROM DPoPKey d WHERE d.isActive = false AND d.lastUsedAt < :before")
    int deleteInactiveKeysOlderThan(@Param("before") Instant before);

    /**
     * Count active keys for a user.
     */
    long countByUserIdAndIsActiveTrue(String userId);

    /**
     * Update last used timestamp.
     */
    @Modifying
    @Query("UPDATE DPoPKey d SET d.lastUsedAt = :now WHERE d.keyId = :keyId")
    int updateLastUsed(@Param("keyId") String keyId, @Param("now") Instant now);
}
