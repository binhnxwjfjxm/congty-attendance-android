package com.hungphat.attendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hungphat.attendance.ui.AttendanceTerminalScreen
import com.hungphat.attendance.ui.theme.CongTyAttendanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CongTyAttendanceTheme {
                AttendanceTerminalScreen()
            }
        }
    }
}
