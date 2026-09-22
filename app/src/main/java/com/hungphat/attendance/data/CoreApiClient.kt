package com.hungphat.attendance.data

import com.hungphat.attendance.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class AdminSession(
    val token: String,
    val displayName: String,
    val expiresAt: String?,
    val permissions: Set<String>,
)

data class EmployeeSummary(
    val id: String,
    val code: String,
    val fullName: String,
    val departmentName: String?,
    val positionName: String?,
)

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(
        val message: String,
        val code: String? = null,
        val retryable: Boolean = false,
    ) : ApiResult<Nothing>
}

sealed interface LoginResult {
    data class Success(val session: AdminSession) : LoginResult
    data class ChallengeRequired(val message: String) : LoginResult
    data class Failure(val message: String, val retryable: Boolean = false) : LoginResult
}

object CoreApiClient {
    const val EMPLOYEE_READ_PERMISSION = "core.employee.read"
    const val EMPLOYEE_WRITE_PERMISSION = "core.employee.write"

    private val baseUrl: String
        get() = BuildConfig.CORE_API_BASE_URL.trimEnd('/')

    suspend fun login(
        loginName: String,
        password: String,
        ownerCode: String? = null,
    ): LoginResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("loginName", loginName.trim())
            .put("password", password)
            .put("sourceApp", BuildConfig.ATTENDANCE_SOURCE_APP)
        if (!ownerCode.isNullOrBlank()) payload.put("ownerCode", ownerCode.trim())

        val response = request(
            method = "POST",
            path = "/api/internal-auth/login",
            body = payload,
        )
        val error = response.json.optJSONObject("error")
        val errorCode = error?.optString("code")?.takeIf { it.isNotBlank() }

        if (errorCode == "INTERNAL_AUTH_OWNER_CHALLENGE_REQUIRED") {
            return@withContext LoginResult.ChallengeRequired(
                "Tài khoản này cần thêm mã xác minh. Vui lòng nhập mã vừa được gửi.",
            )
        }
        if (response.status !in 200..299) {
            return@withContext LoginResult.Failure(
                message = loginMessage(errorCode, error?.optString("message")),
                retryable = error?.optBoolean("retryable") == true,
            )
        }

        val data = response.json.optJSONObject("data")
            ?: return@withContext LoginResult.Failure("Hệ thống trả về phiên đăng nhập không hợp lệ.")
        val token = data.optString("token").trim()
        val user = data.optJSONObject("user")
        if (token.isBlank() || user == null) {
            return@withContext LoginResult.Failure("Hệ thống trả về phiên đăng nhập không hợp lệ.")
        }

        LoginResult.Success(
            AdminSession(
                token = token,
                displayName = user.optString("employeeFullName").ifBlank { user.optString("loginName") },
                expiresAt = data.optJSONObject("session")?.optString("expiresAt")?.takeIf { it.isNotBlank() },
                permissions = jsonStringSet(user.optJSONArray("permissions")),
            ),
        )
    }

    suspend fun verifySession(session: AdminSession): ApiResult<AdminSession> = withContext(Dispatchers.IO) {
        val response = request(
            method = "GET",
            path = "/api/internal-auth/me",
            token = session.token,
        )
        val error = response.json.optJSONObject("error")
        if (response.status !in 200..299) {
            return@withContext ApiResult.Failure(
                message = authMessage(error?.optString("code"), error?.optString("message")),
                code = error?.optString("code"),
                retryable = error?.optBoolean("retryable") == true,
            )
        }
        val data = response.json.optJSONObject("data")
            ?: return@withContext ApiResult.Failure("Không xác nhận được quyền quản trị.")
        ApiResult.Success(
            session.copy(
                permissions = jsonStringSet(data.optJSONArray("permissions")),
            ),
        )
    }

    suspend fun loadEmployees(token: String): ApiResult<List<EmployeeSummary>> = withContext(Dispatchers.IO) {
        val response = request(
            method = "GET",
            path = "/api/employees?active=true&limit=1000&offset=0",
            token = token,
        )
        val error = response.json.optJSONObject("error")
        if (response.status !in 200..299) {
            return@withContext ApiResult.Failure(
                message = when (response.status) {
                    401 -> "Phiên quản trị đã hết hạn. Vui lòng đăng nhập lại."
                    403 -> "Tài khoản không có quyền xem danh sách nhân sự."
                    else -> error?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "Không tải được danh sách nhân sự. Vui lòng thử lại."
                },
                code = error?.optString("code"),
                retryable = error?.optBoolean("retryable") == true,
            )
        }

        val array = response.json.optJSONArray("data")
            ?: return@withContext ApiResult.Failure("Danh sách nhân sự trả về không hợp lệ.")

        val employees = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val code = item.optString("code").trim()
                val fullName = item.optString("full_name").trim()
                if (id.isBlank() || code.isBlank() || fullName.isBlank()) continue
                val assignment = item.optJSONObject("current_assignment")
                add(
                    EmployeeSummary(
                        id = id,
                        code = code,
                        fullName = fullName,
                        departmentName = assignment?.optString("department_name")?.takeIf { it.isNotBlank() },
                        positionName = assignment?.optString("position_name")?.takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        ApiResult.Success(employees)
    }

    suspend fun logout(token: String) = withContext(Dispatchers.IO) {
        runCatching {
            request(
                method = "POST",
                path = "/api/internal-auth/logout",
                token = token,
            )
        }
        Unit
    }

    private fun request(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null,
    ): HttpResponse {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Request-ID", "attendance-" + UUID.randomUUID())
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer " + token)
            }
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

    private fun jsonStringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun loginMessage(code: String?, fallback: String?): String = when (code) {
        "INTERNAL_AUTH_INVALID_CREDENTIALS" -> "Tên đăng nhập hoặc mật khẩu không đúng."
        "INTERNAL_AUTH_OWNER_CODE_INVALID" -> "Mã xác minh không đúng hoặc đã hết hạn."
        "INTERNAL_AUTH_OWNER_CHALLENGE_UNAVAILABLE" -> "Chưa thể gửi mã xác minh. Vui lòng thử lại sau."
        "INTERNAL_AUTH_NOT_CONFIGURED" -> "Đăng nhập quản trị chưa sẵn sàng trên hệ thống."
        else -> fallback?.takeIf { it.isNotBlank() } ?: "Không đăng nhập được. Vui lòng thử lại."
    }

    private fun authMessage(code: String?, fallback: String?): String = when (code) {
        "INTERNAL_AUTH_SESSION_EXPIRED" -> "Phiên quản trị đã hết hạn. Vui lòng đăng nhập lại."
        "INTERNAL_AUTH_SESSION_REVOKED", "INTERNAL_AUTH_SESSION_INVALID" ->
            "Phiên quản trị không còn hiệu lực. Vui lòng đăng nhập lại."
        else -> fallback?.takeIf { it.isNotBlank() } ?: "Không xác nhận được phiên quản trị."
    }

    private data class HttpResponse(
        val status: Int,
        val json: JSONObject,
    )
}
