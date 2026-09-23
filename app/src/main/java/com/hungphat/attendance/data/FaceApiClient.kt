package com.hungphat.attendance.data

import com.hungphat.attendance.BuildConfig
import com.hungphat.attendance.face.FaceNetEmbedder
import com.hungphat.attendance.security.FaceDeviceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class AttendancePointSummary(
    val id: String,
    val code: String,
    val name: String,
    val branchId: String,
    val branchName: String?,
)

data class FaceTemplateInfo(
    val employeeId: String,
    val version: Int,
    val registeredAt: String?,
)

data class FaceAttendanceSuccess(
    val employeeId: String,
    val employeeCode: String,
    val employeeName: String,
    val eventType: String,
    val movementReason: String?,
    val occurredAt: String?,
    val pointName: String?,
)

object FaceApiClient {
    private val baseUrl: String
        get() = BuildConfig.CORE_API_BASE_URL.trimEnd('/')

    suspend fun loadAttendancePoints(adminToken: String): ApiResult<List<AttendancePointSummary>> =
        withContext(Dispatchers.IO) {
            val response = request(
                method = "GET",
                path = "/api/workforce/attendance/points",
                bearer = adminToken,
            )
            response.failureOrNull()?.let { return@withContext it }
            val data = response.json.optJSONObject("data")
                ?: return@withContext ApiResult.Failure("Danh sách nơi chấm công không hợp lệ.")
            val points = data.optJSONArray("points") ?: JSONArray()
            ApiResult.Success(
                buildList {
                    for (index in 0 until points.length()) {
                        val item = points.optJSONObject(index) ?: continue
                        if (!item.optBoolean("is_active", true)) continue
                        val id = item.optString("id").trim()
                        val code = item.optString("code").trim()
                        val name = item.optString("name").trim()
                        val branchId = item.optString("branch_id").trim()
                        if (id.isBlank() || name.isBlank() || branchId.isBlank()) continue
                        add(
                            AttendancePointSummary(
                                id = id,
                                code = code,
                                name = name,
                                branchId = branchId,
                                branchName = item.optString("branch_name").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                },
            )
        }

    suspend fun provisionDevice(
        adminToken: String,
        point: AttendancePointSummary,
        deviceName: String,
        idempotencyKey: String,
    ): ApiResult<FaceDeviceConfig> = withContext(Dispatchers.IO) {
        val response = request(
            method = "POST",
            path = "/api/workforce/face/devices",
            bearer = adminToken,
            idempotencyKey = idempotencyKey,
            body = JSONObject()
                .put("name", deviceName.trim())
                .put("attendancePointId", point.id),
        )
        response.failureOrNull()?.let { return@withContext it }
        val data = response.json.optJSONObject("data")
            ?: return@withContext ApiResult.Failure("Thông tin thiết bị trả về không hợp lệ.")
        val device = data.optJSONObject("device")
            ?: return@withContext ApiResult.Failure("Thông tin thiết bị trả về không hợp lệ.")
        val credential = data.optString("credential").trim()
        if (credential.isBlank()) {
            return@withContext ApiResult.Failure("Hệ thống không trả về mã xác thực thiết bị.")
        }
        ApiResult.Success(
            FaceDeviceConfig(
                id = device.optString("id"),
                name = device.optString("name"),
                branchId = device.optString("branchId"),
                branchName = device.optString("branchName").takeIf { it.isNotBlank() },
                attendancePointId = device.optString("attendancePointId"),
                attendancePointName = device.optString("attendancePointName").takeIf { it.isNotBlank() },
                credential = credential,
            ),
        )
    }

    suspend fun verifyDevice(deviceCredential: String): ApiResult<FaceDeviceConfig> =
        withContext(Dispatchers.IO) {
            val response = request(
                method = "GET",
                path = "/api/workforce/face/device",
                deviceCredential = deviceCredential,
            )
            response.failureOrNull()?.let { return@withContext it }
            val data = response.json.optJSONObject("data")
                ?: return@withContext ApiResult.Failure("Không xác nhận được thiết bị chấm công.")
            ApiResult.Success(
                FaceDeviceConfig(
                    id = data.optString("id"),
                    name = data.optString("name"),
                    branchId = data.optString("branchId"),
                    branchName = data.optString("branchName").takeIf { it.isNotBlank() },
                    attendancePointId = data.optString("attendancePointId"),
                    attendancePointName = data.optString("attendancePointName").takeIf { it.isNotBlank() },
                    credential = deviceCredential,
                ),
            )
        }

    suspend fun loadTemplateStatus(adminToken: String): ApiResult<List<FaceTemplateInfo>> =
        withContext(Dispatchers.IO) {
            val response = request(
                method = "GET",
                path = "/api/workforce/face/templates",
                bearer = adminToken,
            )
            response.failureOrNull()?.let { return@withContext it }
            val data = response.json.optJSONObject("data")
                ?: return@withContext ApiResult.Failure("Trạng thái đăng ký khuôn mặt không hợp lệ.")
            val templates = data.optJSONArray("templates") ?: JSONArray()
            ApiResult.Success(
                buildList {
                    for (index in 0 until templates.length()) {
                        val item = templates.optJSONObject(index) ?: continue
                        val employeeId = item.optString("employeeId").trim()
                        if (employeeId.isBlank()) continue
                        add(
                            FaceTemplateInfo(
                                employeeId = employeeId,
                                version = item.optInt("version", 1),
                                registeredAt = item.optString("registeredAt").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                },
            )
        }

    suspend fun enrollTemplate(
        adminToken: String,
        employeeId: String,
        embeddings: List<FloatArray>,
        idempotencyKey: String,
    ): ApiResult<FaceTemplateInfo> = withContext(Dispatchers.IO) {
        val vectors = JSONArray()
        embeddings.forEach { vector ->
            val row = JSONArray()
            vector.forEach { row.put(it.toDouble()) }
            vectors.put(row)
        }

        val response = request(
            method = "POST",
            path = "/api/workforce/face/templates",
            bearer = adminToken,
            idempotencyKey = idempotencyKey,
            body = JSONObject()
                .put("employeeId", employeeId)
                .put("modelCode", FaceNetEmbedder.MODEL_CODE)
                .put("embeddings", vectors),
        )
        response.failureOrNull()?.let { return@withContext it }
        val template = response.json.optJSONObject("data")?.optJSONObject("template")
            ?: return@withContext ApiResult.Failure("Không xác nhận được mẫu khuôn mặt vừa lưu.")
        ApiResult.Success(
            FaceTemplateInfo(
                employeeId = template.optString("employeeId"),
                version = template.optInt("version", 1),
                registeredAt = template.optString("registeredAt").takeIf { it.isNotBlank() },
            ),
        )
    }

    suspend fun recordAttendance(
        deviceCredential: String,
        embedding: FloatArray,
        exitReason: String? = null,
        note: String? = null,
        idempotencyKey: String,
    ): ApiResult<FaceAttendanceSuccess> = withContext(Dispatchers.IO) {
        val vector = JSONArray()
        embedding.forEach { vector.put(it.toDouble()) }
        val payload = JSONObject()
            .put("modelCode", FaceNetEmbedder.MODEL_CODE)
            .put("embedding", vector)
        if (!exitReason.isNullOrBlank()) payload.put("exitReason", exitReason)
        if (!note.isNullOrBlank()) payload.put("note", note)

        val response = request(
            method = "POST",
            path = "/api/workforce/face/attendance",
            deviceCredential = deviceCredential,
            idempotencyKey = idempotencyKey,
            body = payload,
        )
        response.failureOrNull()?.let { return@withContext it }
        val data = response.json.optJSONObject("data")
            ?: return@withContext ApiResult.Failure("Kết quả điểm danh không hợp lệ.")
        val employee = data.optJSONObject("employee")
            ?: return@withContext ApiResult.Failure("Không xác định được nhân sự.")
        val event = data.optJSONObject("event")
            ?: return@withContext ApiResult.Failure("Không xác định được sự kiện điểm danh.")
        val point = data.optJSONObject("point")

        ApiResult.Success(
            FaceAttendanceSuccess(
                employeeId = employee.optString("employeeId"),
                employeeCode = employee.optString("employeeCode"),
                employeeName = employee.optString("employeeName"),
                eventType = event.optString("event_type"),
                movementReason = event.optString("movement_reason").takeIf { it.isNotBlank() },
                occurredAt = event.optString("occurred_at").takeIf { it.isNotBlank() },
                pointName = point?.optString("name")?.takeIf { it.isNotBlank() },
            ),
        )
    }

    private fun request(
        method: String,
        path: String,
        bearer: String? = null,
        deviceCredential: String? = null,
        idempotencyKey: String? = null,
        body: JSONObject? = null,
    ): HttpResponse {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Request-ID", "attendance-" + UUID.randomUUID())
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer " + bearer)
            if (!deviceCredential.isNullOrBlank()) {
                setRequestProperty("X-NPP-Face-Device-Token", deviceCredential)
            }
            if (!idempotencyKey.isNullOrBlank()) setRequestProperty("Idempotency-Key", idempotencyKey)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        return try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponse(
                status = status,
                json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() },
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpResponse.failureOrNull(): ApiResult.Failure? {
        if (status in 200..299) return null
        val error = json.optJSONObject("error")
        val code = error?.optString("code")?.takeIf { it.isNotBlank() }
        val publicMessage = when (code) {
            "FACE_DEVICE_UNAUTHORIZED" -> "Máy chấm công chưa được xác thực. Vui lòng thiết lập lại máy."
            "FACE_NOT_RECOGNIZED" -> "Không nhận diện được nhân sự. Vui lòng thử lại."
            "FACE_MATCH_AMBIGUOUS" -> "Chưa xác định chắc chắn nhân sự. Vui lòng thử lại."
            "FACE_TEMPLATE_INVALID" -> "Dữ liệu khuôn mặt chưa đủ chất lượng."
            "FACE_TEMPLATE_UNAVAILABLE" -> "Mẫu khuôn mặt tạm thời chưa sẵn sàng."
            "FACE_SERVICE_NOT_CONFIGURED" -> "Dịch vụ nhận diện khuôn mặt chưa sẵn sàng."
            "EXIT_REASON_REQUIRED" -> "Vui lòng chọn lý do rời nơi làm việc."
            "EXIT_NOTE_REQUIRED" -> "Vui lòng nhập ghi chú cho lý do khác."
            "ATTENDANCE_TOO_SOON" -> "Vừa ghi nhận điểm danh; vui lòng đợi một phút."
            "ATTENDANCE_ALREADY_COMPLETE" -> "Ngày làm việc này đã kết thúc."
            "FACE_ATTENDANCE_NOT_ALLOWED" -> "Chính sách hiện tại chưa cho phép điểm danh bằng khuôn mặt."
            "ATTENDANCE_WORKPLACE_MISMATCH" -> "Máy này không thuộc nơi làm việc đã gắn cho nhân sự."
            else -> error?.optString("message")?.takeIf { it.isNotBlank() }
                ?: "Không thực hiện được thao tác. Vui lòng thử lại."
        }
        return ApiResult.Failure(
            message = publicMessage,
            code = code,
            retryable = error?.optBoolean("retryable") == true,
        )
    }

    private data class HttpResponse(
        val status: Int,
        val json: JSONObject,
    )
}
