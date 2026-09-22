package com.hungphat.attendance.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hungphat.attendance.camera.FaceScanState
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AttendanceTerminalScreen() {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var scanState by remember { mutableStateOf(FaceScanState.WAITING) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) {
            scanState = FaceScanState.WAITING
            isScanning = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (isScanning) {
            ScannerContent(
                state = scanState,
                onStateChanged = { scanState = it },
                onCancel = {
                    isScanning = false
                    scanState = FaceScanState.WAITING
                },
            )
        } else {
            HomeContent(
                permissionDenied = permissionDenied,
                onStartAttendance = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED

                    if (granted) {
                        permissionDenied = false
                        scanState = FaceScanState.WAITING
                        isScanning = true
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
            )
        }
    }
}

@Composable
private fun HomeContent(
    permissionDenied: Boolean,
    onStartAttendance: () -> Unit,
) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1_000)
        }
    }

    val locale = remember { Locale("vi", "VN") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss", locale) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", locale) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "CHẤM CÔNG CÔNG TY",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Nhận diện khuôn mặt",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = now.format(timeFormatter),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = now.format(dateFormatter).replaceFirstChar { it.uppercaseChar() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(42.dp))

        Button(
            onClick = onStartAttendance,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = "Điểm danh",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        if (permissionDenied) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Cần quyền Camera để điểm danh bằng khuôn mặt.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Dữ liệu chấm công sẽ được xác nhận bởi hệ thống Công Ty.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScannerContent(
    state: FaceScanState,
    onStateChanged: (FaceScanState) -> Unit,
    onCancel: () -> Unit,
) {
    val accentColor = if (state == FaceScanState.READY) {
        Color(0xFF1B7F46)
    } else {
        Color.White
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FaceCameraPreview(
            modifier = Modifier.fillMaxSize(),
            onStateChanged = onStateChanged,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.14f)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Điểm danh khuôn mặt",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Text("Hủy")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(0.78f)
                    .border(
                        width = 3.dp,
                        color = accentColor,
                        shape = RoundedCornerShape(36.dp),
                    ),
            )

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(accentColor, CircleShape),
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = state.title(),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        text = state.description(),
                        color = Color.White.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun FaceScanState.title(): String = when (this) {
    FaceScanState.WAITING -> "Đưa khuôn mặt vào khung"
    FaceScanState.MULTIPLE_FACES -> "Chỉ một người trước camera"
    FaceScanState.MOVE_CLOSER -> "Tiến gần camera một chút"
    FaceScanState.LOOK_STRAIGHT -> "Nhìn thẳng vào camera"
    FaceScanState.HOLD_STILL -> "Giữ yên trong giây lát"
    FaceScanState.READY -> "Khuôn mặt đạt yêu cầu"
}

private fun FaceScanState.description(): String = when (this) {
    FaceScanState.WAITING -> "Camera đang chờ nhận diện khuôn mặt."
    FaceScanState.MULTIPLE_FACES -> "Vui lòng để từng nhân sự điểm danh lần lượt."
    FaceScanState.MOVE_CLOSER -> "Đưa khuôn mặt vào gần hơn để hệ thống đọc rõ."
    FaceScanState.LOOK_STRAIGHT -> "Giữ đầu thẳng và nhìn trực tiếp vào camera."
    FaceScanState.HOLD_STILL -> "Hệ thống đang kiểm tra độ ổn định của khuôn mặt."
    FaceScanState.READY -> "Sẵn sàng chuyển sang bước xác minh nhân sự."
}
