package com.hungphat.attendance.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hungphat.attendance.camera.EnrollmentHoldPolicy
import com.hungphat.attendance.camera.EnrollmentPose
import com.hungphat.attendance.camera.EnrollmentPosePolicy
import com.hungphat.attendance.camera.FaceFrame
import com.hungphat.attendance.camera.FaceGuidance
import com.hungphat.attendance.camera.FaceReadinessPolicy
import com.hungphat.attendance.camera.FaceSample
import com.hungphat.attendance.camera.FaceScanState
import com.hungphat.attendance.data.AdminSession
import com.hungphat.attendance.data.ApiResult
import com.hungphat.attendance.data.AttendancePointSummary
import com.hungphat.attendance.data.CoreApiClient
import com.hungphat.attendance.data.EmployeeSummary
import com.hungphat.attendance.data.FaceApiClient
import com.hungphat.attendance.data.IdempotencyKeys
import com.hungphat.attendance.data.LoginResult
import com.hungphat.attendance.face.FaceNetEmbedder
import com.hungphat.attendance.security.DeviceCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val EnrollmentNavy = Color(0xFF102A43)
private val EnrollmentBlue = Color(0xFF1F5F99)
private val EnrollmentTeal = Color(0xFF2C7A7B)
private val EnrollmentGreen = Color(0xFF1B7F46)
private val EnrollmentMuted = Color(0xFF52606D)

private enum class EnrollmentStage {
    LOGIN,
    DEVICE_SETUP,
    EMPLOYEE_LIST,
    SCAN,
    COMPLETE,
}

@Composable
fun EnrollmentWorkflowScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceStore = remember { DeviceCredentialStore(context) }

    var stage by remember { mutableStateOf(EnrollmentStage.LOGIN) }
    var session by remember { mutableStateOf<AdminSession?>(null) }
    var employees by remember { mutableStateOf<List<EmployeeSummary>>(emptyList()) }
    var registeredEmployeeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var attendancePoints by remember { mutableStateOf<List<AttendancePointSummary>>(emptyList()) }
    var selectedEmployee by remember { mutableStateOf<EmployeeSummary?>(null) }
    var loginName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ownerCode by remember { mutableStateOf("") }
    var ownerCodeRequired by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var completedAt by remember { mutableStateOf<ZonedDateTime?>(null) }
    var provisionKey by remember { mutableStateOf<String?>(null) }
    var provisionSignature by remember { mutableStateOf<String?>(null) }

    fun leaveEnrollment() {
        val token = session?.token
        session = null
        if (!token.isNullOrBlank()) {
            scope.launch { CoreApiClient.logout(token) }
        }
        onBack()
    }

    fun loadDirectory(adminSession: AdminSession, after: () -> Unit) {
        scope.launch {
            busy = true
            message = null
            try {
                when (val employeeResult = CoreApiClient.loadEmployees(adminSession.token)) {
                    is ApiResult.Failure -> message = employeeResult.message
                    is ApiResult.Success -> {
                        employees = employeeResult.data
                        when (val templateResult = FaceApiClient.loadTemplateStatus(adminSession.token)) {
                            is ApiResult.Failure -> message = templateResult.message
                            is ApiResult.Success -> {
                                registeredEmployeeIds = templateResult.data.map { it.employeeId }.toSet()
                                after()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                message = "Không tải được dữ liệu đăng ký khuôn mặt. Vui lòng kiểm tra mạng."
            } finally {
                busy = false
            }
        }
    }

    fun startEmployeeScan(employee: EmployeeSummary) {
        selectedEmployee = employee
        message = null
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) stage = EnrollmentStage.SCAN
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && selectedEmployee != null) {
            stage = EnrollmentStage.SCAN
            message = null
        } else if (!granted) {
            message = "Cần cho phép Camera để đăng ký khuôn mặt."
        }
    }

    when (stage) {
        EnrollmentStage.LOGIN -> EnrollmentLoginScreen(
            loginName = loginName,
            password = password,
            ownerCode = ownerCode,
            ownerCodeRequired = ownerCodeRequired,
            busy = busy,
            message = message,
            onLoginNameChange = { loginName = it },
            onPasswordChange = { password = it },
            onOwnerCodeChange = { ownerCode = it },
            onBack = onBack,
            onSubmit = {
                if (loginName.isBlank() || password.isBlank()) {
                    message = "Vui lòng nhập tên đăng nhập và mật khẩu."
                    return@EnrollmentLoginScreen
                }
                scope.launch {
                    busy = true
                    message = null
                    try {
                        when (
                            val login = CoreApiClient.login(
                                loginName = loginName,
                                password = password,
                                ownerCode = ownerCode.takeIf { ownerCodeRequired },
                            )
                        ) {
                            is LoginResult.ChallengeRequired -> {
                                ownerCodeRequired = true
                                ownerCode = ""
                                message = login.message
                            }
                            is LoginResult.Failure -> message = login.message
                            is LoginResult.Success -> {
                                when (val verified = CoreApiClient.verifySession(login.session)) {
                                    is ApiResult.Failure -> {
                                        message = verified.message
                                        CoreApiClient.logout(login.session.token)
                                    }
                                    is ApiResult.Success -> {
                                        val verifiedSession = verified.data
                                        val canEnroll =
                                            verifiedSession.permissions.contains(CoreApiClient.EMPLOYEE_READ_PERMISSION) &&
                                                verifiedSession.permissions.contains(CoreApiClient.EMPLOYEE_WRITE_PERMISSION)
                                        if (!canEnroll) {
                                            message = "Tài khoản này không có quyền quản lý hồ sơ nhân sự."
                                            CoreApiClient.logout(verifiedSession.token)
                                        } else {
                                            session = verifiedSession
                                            password = ""
                                            ownerCode = ""
                                            ownerCodeRequired = false
                                            val storedDevice = deviceStore.load()
                                            if (storedDevice == null) {
                                                when (val points = FaceApiClient.loadAttendancePoints(verifiedSession.token)) {
                                                    is ApiResult.Failure -> message = points.message
                                                    is ApiResult.Success -> {
                                                        attendancePoints = points.data
                                                        stage = EnrollmentStage.DEVICE_SETUP
                                                    }
                                                }
                                            } else {
                                                when (val verifiedDevice = FaceApiClient.verifyDevice(storedDevice.credential)) {
                                                    is ApiResult.Success -> {
                                                        loadDirectory(verifiedSession) {
                                                            stage = EnrollmentStage.EMPLOYEE_LIST
                                                        }
                                                    }
                                                    is ApiResult.Failure -> {
                                                        deviceStore.clear()
                                                        when (val points = FaceApiClient.loadAttendancePoints(verifiedSession.token)) {
                                                            is ApiResult.Failure -> message = points.message
                                                            is ApiResult.Success -> {
                                                                attendancePoints = points.data
                                                                message = "Máy cần được thiết lập lại trước khi đăng ký khuôn mặt."
                                                                stage = EnrollmentStage.DEVICE_SETUP
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        message = "Không kết nối được hệ thống Công Ty. Vui lòng kiểm tra mạng."
                    } finally {
                        busy = false
                    }
                }
            },
        )

        EnrollmentStage.DEVICE_SETUP -> {
            val adminSession = session
            if (adminSession == null) {
                stage = EnrollmentStage.LOGIN
            } else {
                EnrollmentDeviceSetupScreen(
                    points = attendancePoints,
                    busy = busy,
                    message = message,
                    onBack = ::leaveEnrollment,
                    onProvision = { point, deviceName ->
                        scope.launch {
                            busy = true
                            message = null
                            try {
                                val signature = point.id + "|" + deviceName.trim()
                                val key = if (provisionSignature == signature && provisionKey != null) {
                                    provisionKey!!
                                } else {
                                    IdempotencyKeys.create("attendance-face-device").also {
                                        provisionSignature = signature
                                        provisionKey = it
                                    }
                                }
                                when (
                                    val result = FaceApiClient.provisionDevice(
                                        adminToken = adminSession.token,
                                        point = point,
                                        deviceName = deviceName,
                                        idempotencyKey = key,
                                    )
                                ) {
                                    is ApiResult.Failure -> message = result.message
                                    is ApiResult.Success -> {
                                        deviceStore.save(result.data)
                                        provisionKey = null
                                        provisionSignature = null
                                        loadDirectory(adminSession) {
                                            stage = EnrollmentStage.EMPLOYEE_LIST
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                message = "Không thiết lập được máy chấm công. Vui lòng thử lại."
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
            }
        }

        EnrollmentStage.EMPLOYEE_LIST -> {
            val filteredEmployees = remember(employees, query) {
                val term = query.trim().lowercase(Locale.ROOT)
                if (term.isBlank()) {
                    employees
                } else {
                    employees.filter { employee ->
                        employee.code.lowercase(Locale.ROOT).contains(term) ||
                            employee.fullName.lowercase(Locale.ROOT).contains(term) ||
                            employee.departmentName.orEmpty().lowercase(Locale.ROOT).contains(term)
                    }
                }
            }
            EnrollmentEmployeeListScreen(
                adminName = session?.displayName.orEmpty(),
                query = query,
                employees = filteredEmployees,
                registeredEmployeeIds = registeredEmployeeIds,
                busy = busy,
                message = message,
                onQueryChange = { query = it },
                onBack = ::leaveEnrollment,
                onRefresh = {
                    val adminSession = session ?: return@EnrollmentEmployeeListScreen
                    loadDirectory(adminSession) {}
                },
                onSelect = { employee ->
                    startEmployeeScan(employee)
                    if (stage != EnrollmentStage.SCAN) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
            )
        }

        EnrollmentStage.SCAN -> {
            val employee = selectedEmployee
            val adminToken = session?.token
            if (employee == null || adminToken.isNullOrBlank()) {
                stage = EnrollmentStage.EMPLOYEE_LIST
            } else {
                EnrollmentCaptureScreen(
                    employee = employee,
                    adminToken = adminToken,
                    onBack = { stage = EnrollmentStage.EMPLOYEE_LIST },
                    onComplete = {
                        registeredEmployeeIds = registeredEmployeeIds + employee.id
                        completedAt = ZonedDateTime.now()
                        stage = EnrollmentStage.COMPLETE
                    },
                )
            }
        }

        EnrollmentStage.COMPLETE -> {
            val employee = selectedEmployee
            if (employee == null) {
                stage = EnrollmentStage.EMPLOYEE_LIST
            } else {
                EnrollmentCompleteScreen(
                    employee = employee,
                    completedAt = completedAt ?: ZonedDateTime.now(),
                    onNextEmployee = {
                        selectedEmployee = null
                        completedAt = null
                        query = ""
                        stage = EnrollmentStage.EMPLOYEE_LIST
                    },
                    onHome = ::leaveEnrollment,
                )
            }
        }
    }
}

@Composable
private fun EnrollmentLoginScreen(
    loginName: String,
    password: String,
    ownerCode: String,
    ownerCodeRequired: Boolean,
    busy: Boolean,
    message: String?,
    onLoginNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onOwnerCodeChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    EnrollmentSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(22.dp),
        ) {
            EnrollmentTopBar(title = "Đăng ký khuôn mặt", onBack = onBack)
            Spacer(modifier = Modifier.height(22.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = Color.White,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        text = "Xác thực quản trị",
                        color = EnrollmentNavy,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Đăng nhập bằng tài khoản Công Ty có quyền quản lý nhân sự.",
                        color = EnrollmentMuted,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    OutlinedTextField(
                        value = loginName,
                        onValueChange = onLoginNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tên đăng nhập") },
                        singleLine = true,
                        enabled = !busy,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mật khẩu") },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (ownerCodeRequired) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = ownerCode,
                            onValueChange = onOwnerCodeChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Mã xác minh") },
                            singleLine = true,
                            enabled = !busy,
                        )
                    }
                    EnrollmentMessage(message)
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onSubmit,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        enabled = !busy,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EnrollmentBlue),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Text(if (ownerCodeRequired) "Xác minh" else "Đăng nhập", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnrollmentDeviceSetupScreen(
    points: List<AttendancePointSummary>,
    busy: Boolean,
    message: String?,
    onBack: () -> Unit,
    onProvision: (AttendancePointSummary, String) -> Unit,
) {
    var deviceName by remember { mutableStateOf("Máy chấm công - " + Build.MODEL.take(60)) }
    EnrollmentSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(18.dp),
        ) {
            EnrollmentTopBar(title = "Thiết lập máy chấm công", onBack = onBack)
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it.take(128) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tên máy") },
                singleLine = true,
                enabled = !busy,
            )
            EnrollmentMessage(message)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Chọn đúng nơi làm việc của máy này",
                color = EnrollmentNavy,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (points.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                ) {
                    Text(
                        text = "Chưa có nơi chấm công đang hoạt động. Hãy thiết lập nơi chấm công trên Công Ty trước.",
                        modifier = Modifier.padding(18.dp),
                        color = EnrollmentMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(points, key = { it.id }) { point ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            shadowElevation = 3.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = point.branchName ?: point.name,
                                        color = EnrollmentNavy,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = point.name,
                                        color = EnrollmentMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Button(
                                    onClick = { onProvision(point, deviceName.trim()) },
                                    enabled = !busy && deviceName.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text("Chọn")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnrollmentEmployeeListScreen(
    adminName: String,
    query: String,
    employees: List<EmployeeSummary>,
    registeredEmployeeIds: Set<String>,
    busy: Boolean,
    message: String?,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (EmployeeSummary) -> Unit,
) {
    EnrollmentSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(18.dp),
        ) {
            EnrollmentTopBar(
                title = "Chọn nhân sự",
                subtitle = if (adminName.isBlank()) null else "Quản trị: " + adminName,
                onBack = onBack,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tìm theo tên, mã hoặc phòng/bộ phận") },
                singleLine = true,
            )
            EnrollmentMessage(message)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = employees.size.toString() + " nhân sự",
                    modifier = Modifier.weight(1f),
                    color = EnrollmentMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onRefresh, enabled = !busy) {
                    Text(if (busy) "Đang tải..." else "Tải lại")
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(employees, key = { it.id }) { employee ->
                    EmployeeEnrollmentCard(
                        employee = employee,
                        registered = registeredEmployeeIds.contains(employee.id),
                        onSelect = { onSelect(employee) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmployeeEnrollmentCard(
    employee: EmployeeSummary,
    registered: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = EnrollmentTeal.copy(alpha = 0.12f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = employee.fullName.take(1).uppercase(Locale("vi", "VN")),
                            color = EnrollmentTeal,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(employee.fullName, color = EnrollmentNavy, fontWeight = FontWeight.Bold)
                    Text("Mã nhân sự: " + employee.code, color = EnrollmentMuted, style = MaterialTheme.typography.bodySmall)
                    val organization = listOfNotNull(employee.departmentName, employee.positionName).joinToString(" • ")
                    if (organization.isNotBlank()) {
                        Text(organization, color = EnrollmentMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFE8EEF3))
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (registered) "Đã đăng ký" else "Chưa đăng ký",
                    modifier = Modifier.weight(1f),
                    color = if (registered) EnrollmentGreen else EnrollmentMuted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    onClick = onSelect,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EnrollmentBlue),
                ) {
                    Text(if (registered) "Cập nhật" else "Đăng ký")
                }
            }
        }
    }
}

@Composable
private fun EnrollmentCaptureScreen(
    employee: EmployeeSummary,
    adminToken: String,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val embedder = remember { FaceNetEmbedder(context) }
    DisposableEffect(Unit) {
        onDispose { embedder.close() }
    }

    val poses = remember {
        listOf(EnrollmentPose.FRONT, EnrollmentPose.TURN_LEFT, EnrollmentPose.TURN_RIGHT)
    }
    var stepIndex by remember(employee.id) { mutableIntStateOf(0) }
    var stableSinceMs by remember(employee.id) { mutableLongStateOf(0L) }
    var readyToCapture by remember(employee.id) { mutableStateOf(false) }
    var processing by remember(employee.id) { mutableStateOf(false) }
    var guidance by remember(employee.id) { mutableStateOf("Đưa khuôn mặt vào khung.") }
    var embeddings by remember(employee.id) { mutableStateOf<List<FloatArray>>(emptyList()) }
    var saveKey by remember(employee.id) { mutableStateOf<String?>(null) }
    var saveError by remember(employee.id) { mutableStateOf<String?>(null) }

    val pose = poses[stepIndex.coerceIn(poses.indices)]

    fun saveTemplate(vectors: List<FloatArray>) {
        if (processing) return
        processing = true
        saveError = null
        val key = saveKey ?: IdempotencyKeys.create("attendance-face-enroll").also { saveKey = it }
        scope.launch {
            try {
                when (
                    val result = FaceApiClient.enrollTemplate(
                        adminToken = adminToken,
                        employeeId = employee.id,
                        embeddings = vectors,
                        idempotencyKey = key,
                    )
                ) {
                    is ApiResult.Success -> onComplete()
                    is ApiResult.Failure -> saveError = result.message
                }
            } catch (_: Exception) {
                saveError = "Không lưu được mẫu khuôn mặt. Vui lòng kiểm tra mạng và thử lại."
            } finally {
                processing = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FaceCameraPreview(
            modifier = Modifier.fillMaxSize(),
            onStateChanged = { _: FaceScanState -> },
            onFrameChanged = { frame ->
                val matches = EnrollmentPosePolicy.matches(pose, frame)
                if (matches) {
                    val nowMs = SystemClock.elapsedRealtime()
                    if (stableSinceMs == 0L) stableSinceMs = nowMs
                    val remainingMs = EnrollmentHoldPolicy.remainingMillis(stableSinceMs, nowMs)
                    readyToCapture = remainingMs == 0L
                    guidance = if (readyToCapture) {
                        "Đã đủ ổn định. Đang lấy mẫu..."
                    } else {
                        "Đúng vị trí. Giữ yên thêm " + holdSecondsText(remainingMs) + " giây."
                    }
                } else {
                    stableSinceMs = 0L
                    readyToCapture = false
                    guidance = enrollmentGuidance(pose, frame, matches)
                }
            },
            onFaceSample = { sample: FaceSample ->
                if (!readyToCapture || processing || !EnrollmentPosePolicy.matches(pose, sample.frame)) {
                    sample.bitmap.recycle()
                    return@FaceCameraPreview
                }
                readyToCapture = false
                stableSinceMs = 0L
                processing = true
                scope.launch {
                    try {
                        val embedding = withContext(Dispatchers.Default) {
                            embedder.embed(sample.bitmap)
                        }
                        sample.bitmap.recycle()
                        val updated = embeddings + embedding
                        embeddings = updated
                        if (stepIndex >= poses.lastIndex) {
                            processing = false
                            saveTemplate(updated)
                        } else {
                            stepIndex += 1
                            processing = false
                        }
                    } catch (_: Exception) {
                        sample.bitmap.recycle()
                        guidance = "Không xử lý được mẫu này. Vui lòng thử lại."
                        processing = false
                    }
                }
            },
        )

        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.62f),
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onBack,
                    enabled = !processing,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Text("‹ Quay lại")
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(employee.fullName, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(employee.code, color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.80f)
                    .aspectRatio(0.78f)
                    .border(
                        3.dp,
                        if (readyToCapture) EnrollmentGreen else Color.White,
                        RoundedCornerShape(38.dp),
                    ),
            )

            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF0D1B2A).copy(alpha = 0.86f),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (embeddings.size >= poses.size) "Đang lưu mẫu khuôn mặt" else "Mẫu " + (stepIndex + 1) + "/" + poses.size,
                        color = Color.White.copy(alpha = 0.74f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = if (embeddings.size >= poses.size) "Đang hoàn tất" else poseTitle(pose),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = saveError ?: if (processing) "Vui lòng giữ nguyên trong giây lát." else guidance,
                        color = Color.White.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center,
                    )
                    if (saveError != null && embeddings.size >= poses.size) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { saveTemplate(embeddings) },
                            enabled = !processing,
                        ) {
                            Text("Lưu lại")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EnrollmentCompleteScreen(
    employee: EmployeeSummary,
    completedAt: ZonedDateTime,
    onNextEmployee: () -> Unit,
    onHome: () -> Unit,
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy", Locale("vi", "VN"))
    }
    EnrollmentSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier.size(62.dp),
                        shape = CircleShape,
                        color = EnrollmentGreen.copy(alpha = 0.12f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✓", color = EnrollmentGreen, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Đăng ký thành công", color = EnrollmentNavy, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(employee.fullName, color = EnrollmentNavy, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Mã nhân sự: " + employee.code, color = EnrollmentMuted)
                    Text(completedAt.format(formatter), color = EnrollmentMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Mẫu nhận diện đã được lưu vào hệ thống Công Ty. Ảnh camera không được lưu lâu dài.",
                        color = EnrollmentMuted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onNextEmployee,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Đăng ký người tiếp theo", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onHome,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Về màn hình chính")
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EnrollmentMessage(message: String?) {
    if (message.isNullOrBlank()) return
    Spacer(modifier = Modifier.height(10.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF4F7FA),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            color = EnrollmentMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EnrollmentSurface(
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF3F6F9))) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White,
                        Color(0xFFF3F6F9),
                        EnrollmentNavy.copy(alpha = 0.10f),
                    ),
                ),
            ),
        )
        content()
    }
}

@Composable
private fun EnrollmentTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("‹  Quay lại") }
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(title, color = EnrollmentNavy, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = EnrollmentMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun poseTitle(pose: EnrollmentPose): String = when (pose) {
    EnrollmentPose.FRONT -> "Nhìn thẳng"
    EnrollmentPose.TURN_LEFT -> "Nghiêng trái nhẹ"
    EnrollmentPose.TURN_RIGHT -> "Nghiêng phải nhẹ"
}

private fun enrollmentGuidance(
    pose: EnrollmentPose,
    frame: FaceFrame,
    matches: Boolean,
): String = when {
    frame.faceCount == 0 -> "Đưa khuôn mặt vào giữa khung."
    frame.faceCount > 1 -> "Vui lòng để một người trước camera."
    FaceReadinessPolicy.evaluate(frame) == FaceGuidance.MOVE_CLOSER -> "Tiến gần camera hơn một chút."
    abs(frame.rollDegrees) > 15f -> "Giữ đầu thẳng, không nghiêng sang vai."
    matches -> "Đúng vị trí. Giữ yên trong giây lát."
    else -> when (pose) {
        EnrollmentPose.FRONT -> "Nhìn thẳng vào camera."
        EnrollmentPose.TURN_LEFT -> "Xoay mặt nhẹ sang trái."
        EnrollmentPose.TURN_RIGHT -> "Xoay mặt nhẹ sang phải."
    }
}

private fun holdSecondsText(remainingMs: Long): String {
    val tenths = ((remainingMs.coerceAtLeast(0L) + 99L) / 100L).coerceAtLeast(1L)
    return (tenths / 10L).toString() + "." + (tenths % 10L).toString()
}
