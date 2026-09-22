package com.hungphat.attendance.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hungphat.attendance.R
import com.hungphat.attendance.camera.FaceScanState
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BrandNavy = Color(0xFF102A43)
private val BrandBlue = Color(0xFF1F5F99)
private val BrandTeal = Color(0xFF2C7A7B)
private val BrandGreen = Color(0xFF1B7F46)
private val BrandMuted = Color(0xFF52606D)
private val SoftBackground = Color(0xFFF3F6F9)

private enum class TerminalPage {
    HOME,
    ATTENDANCE,
    ENROLLMENT,
}

@Composable
fun AttendanceTerminalScreen() {
    val context = LocalContext.current
    var page by remember { mutableStateOf(TerminalPage.HOME) }
    var permissionDenied by remember { mutableStateOf(false) }
    var scanState by remember { mutableStateOf(FaceScanState.WAITING) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) {
            scanState = FaceScanState.WAITING
            page = TerminalPage.ATTENDANCE
        }
    }

    when (page) {
        TerminalPage.HOME -> HomeContent(
            permissionDenied = permissionDenied,
            onStartAttendance = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED

                if (granted) {
                    permissionDenied = false
                    scanState = FaceScanState.WAITING
                    page = TerminalPage.ATTENDANCE
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onStartEnrollment = {
                page = TerminalPage.ENROLLMENT
            },
        )

        TerminalPage.ATTENDANCE -> ScannerContent(
            state = scanState,
            onStateChanged = { scanState = it },
            onCancel = {
                scanState = FaceScanState.WAITING
                page = TerminalPage.HOME
            },
        )

        TerminalPage.ENROLLMENT -> EnrollmentContent(
            onBack = { page = TerminalPage.HOME },
        )
    }
}

@Composable
private fun BrandBackground(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftBackground),
    ) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    alpha = 0.15f,
                    scaleX = 1.75f,
                    scaleY = 1.75f,
                )
                .blur(30.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.78f),
                            SoftBackground.copy(alpha = 0.88f),
                            BrandNavy.copy(alpha = 0.18f),
                        ),
                    ),
                ),
        )

        content()
    }
}

@Composable
private fun HomeContent(
    permissionDenied: Boolean,
    onStartAttendance: () -> Unit,
    onStartEnrollment: () -> Unit,
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

    BrandBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 6.dp,
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = "Logo Công Ty",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(width = 98.dp, height = 58.dp)
                            .padding(8.dp),
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "MÁY CHẤM CÔNG",
                        color = BrandNavy,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Nhận diện khuôn mặt",
                        color = BrandMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.94f),
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = now.format(timeFormatter),
                        style = MaterialTheme.typography.displayLarge,
                        color = BrandNavy,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = now.format(dateFormatter).replaceFirstChar { it.uppercaseChar() },
                        style = MaterialTheme.typography.titleMedium,
                        color = BrandMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onStartAttendance,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandBlue,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "01",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = "Điểm danh",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Đứng trước camera để bắt đầu.",
                            color = Color.White.copy(alpha = 0.84f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onStartEnrollment,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.96f),
                    contentColor = BrandNavy,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = BrandTeal.copy(alpha = 0.12f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "02",
                                color = BrandTeal,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = "Đăng ký khuôn mặt",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Dành cho quản trị đăng ký hoặc cập nhật nhân sự.",
                            color = BrandMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = "›",
                        color = BrandTeal,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (permissionDenied) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                ) {
                    Text(
                        text = "Cần cho phép Camera để sử dụng chức năng điểm danh.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.78f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(Color(0xFF8A94A6), CircleShape),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Chưa kết nối hệ thống",
                        modifier = Modifier.weight(1f),
                        color = BrandMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Đồng bộ: —",
                        color = Color(0xFF7B8794),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun EnrollmentContent(
    onBack: () -> Unit,
) {
    BrandBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("‹  Quay lại")
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Đăng ký khuôn mặt",
                    color = BrandNavy,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = "Logo Công Ty",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .height(72.dp),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Khu vực dành cho quản trị",
                        color = BrandNavy,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Lô A đã chuẩn bị giao diện cho chế độ đăng ký. Xác thực quản trị, chọn nhân sự và quét đăng ký sẽ được nối theo contract thật ở Lô B.",
                        color = BrandMuted,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF4F7FA),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF8A94A6), CircleShape),
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Column {
                                Text(
                                    text = "Chưa mở đăng ký",
                                    color = BrandNavy,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Không tạo nhân sự hoặc dữ liệu tạm trên thiết bị.",
                                    color = BrandMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerContent(
    state: FaceScanState,
    onStateChanged: (FaceScanState) -> Unit,
    onCancel: () -> Unit,
) {
    val accentColor = if (state == FaceScanState.READY) {
        BrandGreen
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
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.56f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.68f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.95f),
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = "Logo Công Ty",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(width = 76.dp, height = 46.dp)
                            .padding(6.dp),
                    )
                }

                Spacer(modifier = Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Điểm danh",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Nhìn vào camera phía trước",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Text("Thoát")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.80f)
                    .aspectRatio(0.78f)
                    .border(
                        width = 3.dp,
                        color = accentColor,
                        shape = RoundedCornerShape(38.dp),
                    ),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0D1B2A).copy(alpha = 0.84f),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
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
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = state.description(),
                        color = Color.White.copy(alpha = 0.84f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun FaceScanState.title(): String = when (this) {
    FaceScanState.WAITING -> "Đưa khuôn mặt vào khung"
    FaceScanState.MULTIPLE_FACES -> "Chỉ một người trước camera"
    FaceScanState.MOVE_CLOSER -> "Tiến gần hơn"
    FaceScanState.LOOK_STRAIGHT -> "Nhìn thẳng"
    FaceScanState.HOLD_STILL -> "Giữ yên"
    FaceScanState.READY -> "Đang xác minh"
}

private fun FaceScanState.description(): String = when (this) {
    FaceScanState.WAITING -> "Đặt khuôn mặt ở giữa khung để bắt đầu."
    FaceScanState.MULTIPLE_FACES -> "Vui lòng để từng nhân sự điểm danh lần lượt."
    FaceScanState.MOVE_CLOSER -> "Tiến gần camera để khuôn mặt hiển thị rõ hơn."
    FaceScanState.LOOK_STRAIGHT -> "Giữ đầu thẳng và nhìn trực tiếp vào camera."
    FaceScanState.HOLD_STILL -> "Giữ nguyên vị trí trong giây lát."
    FaceScanState.READY -> "Khuôn mặt đã đạt điều kiện để chuyển sang bước nhận diện."
}
