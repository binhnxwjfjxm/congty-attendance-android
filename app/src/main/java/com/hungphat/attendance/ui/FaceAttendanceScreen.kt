package com.hungphat.attendance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hungphat.attendance.camera.FaceSample
import com.hungphat.attendance.camera.FaceScanState
import com.hungphat.attendance.data.ApiResult
import com.hungphat.attendance.data.FaceApiClient
import com.hungphat.attendance.data.FaceAttendanceSuccess
import com.hungphat.attendance.data.IdempotencyKeys
import com.hungphat.attendance.face.FaceNetEmbedder
import com.hungphat.attendance.security.DeviceCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AttendanceGreen = Color(0xFF1B7F46)
private val AttendanceNavy = Color(0xFF102A43)

@Composable
fun FaceAttendanceScreen(
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceStore = remember { DeviceCredentialStore(context) }
    val device = remember { deviceStore.load() }
    val embedder = remember { FaceNetEmbedder(context) }

    var scanState by remember { mutableStateOf(FaceScanState.WAITING) }
    var deviceReady by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<FaceAttendanceSuccess?>(null) }
    var pendingExitEmbedding by remember { mutableStateOf<FloatArray?>(null) }
    var retryAfter by remember { mutableLongStateOf(0L) }
    var retryEmbedding by remember { mutableStateOf<FloatArray?>(null) }
    var retryKey by remember { mutableStateOf<String?>(null) }
    var exitKeys by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    DisposableEffect(Unit) {
        onDispose { embedder.close() }
    }

    LaunchedEffect(device?.credential) {
        if (device == null) {
            message = "Máy chấm công chưa được thiết lập. Vào Đăng ký khuôn mặt bằng tài khoản quản trị để thiết lập máy."
            return@LaunchedEffect
        }
        processing = true
        when (val result = FaceApiClient.verifyDevice(device.credential)) {
            is ApiResult.Success -> {
                deviceReady = true
                message = null
            }
            is ApiResult.Failure -> {
                deviceReady = false
                message = result.message
            }
        }
        processing = false
    }

    LaunchedEffect(success) {
        if (success != null) {
            delay(3_000)
            onExit()
        }
    }

    fun submitAttendance(
        embedding: FloatArray,
        exitReason: String? = null,
        idempotencyKey: String,
    ) {
        val currentDevice = device ?: return
        if (processing) return
        processing = true
        message = if (exitReason == null) "Đang xác minh nhân sự..." else "Đang ghi nhận..."
        scope.launch {
            try {
                when (
                    val result = FaceApiClient.recordAttendance(
                        deviceCredential = currentDevice.credential,
                        embedding = embedding,
                        exitReason = exitReason,
                        idempotencyKey = idempotencyKey,
                    )
                ) {
                    is ApiResult.Success -> {
                        pendingExitEmbedding = null
                        retryEmbedding = null
                        retryKey = null
                        exitKeys = emptyMap()
                        success = result.data
                        message = null
                    }
                    is ApiResult.Failure -> {
                        if (result.code == "EXIT_REASON_REQUIRED") {
                            pendingExitEmbedding = embedding
                            retryEmbedding = null
                            retryKey = null
                            message = "Chọn lý do rời nơi làm việc."
                        } else {
                            message = result.message
                            if (exitReason == null && result.retryable) {
                                retryEmbedding = embedding
                                retryKey = idempotencyKey
                            } else if (exitReason == null) {
                                retryEmbedding = null
                                retryKey = null
                            }
                            retryAfter = System.currentTimeMillis() + 1_500
                        }
                    }
                }
            } catch (_: Exception) {
                message = "Không kết nối được hệ thống Công Ty. Vui lòng thử lại."
                if (exitReason == null) {
                    retryEmbedding = embedding
                    retryKey = idempotencyKey
                }
                retryAfter = System.currentTimeMillis() + 1_500
            } finally {
                processing = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (deviceReady && success == null) {
            FaceCameraPreview(
                modifier = Modifier.fillMaxSize(),
                onStateChanged = { scanState = it },
                onFaceSample = { sample: FaceSample ->
                    if (
                        scanState != FaceScanState.READY ||
                        processing ||
                        pendingExitEmbedding != null ||
                        retryEmbedding != null ||
                        System.currentTimeMillis() < retryAfter
                    ) {
                        sample.bitmap.recycle()
                    } else {
                        processing = true
                        scope.launch {
                            try {
                                val embedding = withContext(Dispatchers.Default) {
                                    embedder.embed(sample.bitmap)
                                }
                                sample.bitmap.recycle()
                                processing = false
                                val key = IdempotencyKeys.create("attendance-face-event")
                                retryEmbedding = embedding
                                retryKey = key
                                submitAttendance(
                                    embedding = embedding,
                                    idempotencyKey = key,
                                )
                            } catch (_: Exception) {
                                sample.bitmap.recycle()
                                processing = false
                                message = "Chưa xử lý được khuôn mặt. Vui lòng thử lại."
                            }
                        }
                    }
                },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF142B3D)),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.60f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Điểm danh",
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = onExit,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Text("Thoát")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (success != null) {
                AttendanceSuccessCard(success!!)
            } else if (pendingExitEmbedding != null) {
                ExitReasonCard(
                    busy = processing,
                    message = message,
                    onChoose = { reason ->
                        pendingExitEmbedding?.let { embedding ->
                            val key = exitKeys[reason]
                                ?: IdempotencyKeys.create("attendance-face-exit").also {
                                    exitKeys = exitKeys + (reason to it)
                                }
                            submitAttendance(
                                embedding = embedding,
                                exitReason = reason,
                                idempotencyKey = key,
                            )
                        }
                    },
                )
            } else if (!deviceReady) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.95f),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (processing) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Text(
                            text = message ?: "Đang kiểm tra máy chấm công...",
                            color = AttendanceNavy,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.80f)
                        .aspectRatio(0.78f)
                        .border(
                            width = 3.dp,
                            color = if (scanState == FaceScanState.READY) AttendanceGreen else Color.White,
                            shape = RoundedCornerShape(38.dp),
                        ),
                )
                Spacer(modifier = Modifier.height(22.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0D1B2A).copy(alpha = 0.86f),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (processing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = message ?: scanStateTitle(scanState),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (message == null) scanStateDescription(scanState) else " ",
                            color = Color.White.copy(alpha = 0.80f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        if (!processing && retryEmbedding != null && retryKey != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val embedding = retryEmbedding
                                    val key = retryKey
                                    if (embedding != null && key != null) {
                                        submitAttendance(
                                            embedding = embedding,
                                            idempotencyKey = key,
                                        )
                                    }
                                },
                            ) {
                                Text("Thử lại")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ExitReasonCard(
    busy: Boolean,
    message: String?,
    onChoose: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.96f),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Bạn đang rời nơi làm việc",
                color = AttendanceNavy,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Chọn đúng lý do để ghi nhận.",
                color = Color(0xFF52606D),
            )
            if (!message.isNullOrBlank() && message != "Chọn lý do rời nơi làm việc.") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            val choices = listOf(
                "END_WORK" to "Kết thúc làm việc",
                "WORK_BUSINESS" to "Ra ngoài làm công việc",
                "PERSONAL" to "Ra ngoài việc cá nhân",
                "BREAK" to "Nghỉ giữa ca",
            )
            choices.forEach { (value, label) ->
                Button(
                    onClick = { onChoose(value) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun AttendanceSuccessCard(result: FaceAttendanceSuccess) {
    val now = remember(result.eventType) {
        ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale("vi", "VN")))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.97f),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = CircleShape,
                color = AttendanceGreen.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "✓",
                        color = AttendanceGreen,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = result.employeeName,
                color = AttendanceNavy,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = eventLabel(result.eventType),
                color = AttendanceGreen,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = now,
                color = Color(0xFF52606D),
                style = MaterialTheme.typography.titleMedium,
            )
            result.pointName?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, color = Color(0xFF52606D), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Đã ghi nhận thành công",
                color = Color(0xFF52606D),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun eventLabel(value: String): String = when (value) {
    "CHECK_IN" -> "Vào làm"
    "TEMP_EXIT" -> "Ra ngoài"
    "RETURN" -> "Quay lại làm việc"
    "CHECK_OUT" -> "Kết thúc làm việc"
    else -> "Điểm danh"
}

private fun scanStateTitle(state: FaceScanState): String = when (state) {
    FaceScanState.WAITING -> "Đưa khuôn mặt vào khung"
    FaceScanState.MULTIPLE_FACES -> "Chỉ một người trước camera"
    FaceScanState.MOVE_CLOSER -> "Tiến gần hơn"
    FaceScanState.LOOK_STRAIGHT -> "Nhìn thẳng"
    FaceScanState.HOLD_STILL -> "Giữ yên"
    FaceScanState.READY -> "Đang xác minh"
}

private fun scanStateDescription(state: FaceScanState): String = when (state) {
    FaceScanState.WAITING -> "Đặt khuôn mặt ở giữa khung để bắt đầu."
    FaceScanState.MULTIPLE_FACES -> "Vui lòng điểm danh lần lượt từng người."
    FaceScanState.MOVE_CLOSER -> "Tiến gần camera để khuôn mặt hiển thị rõ hơn."
    FaceScanState.LOOK_STRAIGHT -> "Giữ đầu thẳng và nhìn trực tiếp vào camera."
    FaceScanState.HOLD_STILL -> "Giữ nguyên vị trí trong giây lát."
    FaceScanState.READY -> "Khuôn mặt đã đạt điều kiện nhận diện."
}
