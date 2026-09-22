package com.hungphat.attendance.camera

import android.graphics.Bitmap

data class FaceSample(
    val bitmap: Bitmap,
    val frame: FaceFrame,
)
