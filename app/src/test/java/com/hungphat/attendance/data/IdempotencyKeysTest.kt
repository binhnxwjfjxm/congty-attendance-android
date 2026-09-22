package com.hungphat.attendance.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class IdempotencyKeysTest {
    @Test
    fun canonical_key_matches_shared_contract_shape() {
        val uuid = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val key = IdempotencyKeys.create(" Attendance FACE Event ", uuid)
        assertEquals(
            "attendance-face-event-123e4567-e89b-42d3-a456-426614174000",
            key,
        )
        assertTrue(key.matches(Regex("^[A-Za-z0-9._-]+$")))
    }
}
