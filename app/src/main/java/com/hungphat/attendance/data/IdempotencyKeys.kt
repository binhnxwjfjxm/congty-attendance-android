package com.hungphat.attendance.data

import java.util.UUID

object IdempotencyKeys {
    private val unsafe = Regex("[^a-z0-9._-]+")
    private val repeatedDash = Regex("-+")
    private val trimEdges = Regex("^[._-]+|[._-]+$")
    private const val MAX_OPERATION_LENGTH = 80

    fun create(operation: String, uuid: UUID = UUID.randomUUID()): String {
        val normalized = operation
            .trim()
            .lowercase()
            .replace(unsafe, "-")
            .replace(repeatedDash, "-")
            .replace(trimEdges, "")
            .take(MAX_OPERATION_LENGTH)
            .trimEnd('.', '_', '-')
        require(normalized.isNotBlank()) { "idempotency_operation_required" }
        return "${normalized}-${uuid.toString().lowercase()}"
    }
}
