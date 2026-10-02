/* Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package group.worldstandard.pudel.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class SessionCookieServiceTest {

    private final SessionCookieService service = new SessionCookieService(
            new ObjectMapper(),
            "pudel_session",
            "unit-test-cookie-secret",
            "ignored/private.key",
            604800000L
    );

    @Test
    void encryptsAndDecryptsKeyId() {
        Instant expiry = Instant.now().plusSeconds(3600);
        String cookie = service.encrypt("unit-key-id", expiry);

        assertEquals("unit-key-id", service.decrypt(cookie));
    }

    @Test
    void rejectsTamperedCookieValue() {
        Instant expiry = Instant.now().plusSeconds(3600);
        String cookie = service.encrypt("unit-key-id", expiry);
        StringBuilder tampered = new StringBuilder(cookie);
        int lastDot = tampered.lastIndexOf(".");
        tampered.setCharAt(lastDot - 1, tampered.charAt(lastDot - 1) == 'A' ? 'B' : 'A');

        assertThrows(SessionCookieService.InvalidSessionCookieException.class,
                () -> service.decrypt(tampered.toString()));
    }
}
