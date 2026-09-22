import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

object FaceNetModelIntegrity {
    fun gitBlobSha(file: File): String {
        val bytes = file.readBytes()
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

val prepareFaceNetModel = tasks.register("prepareFaceNetModel") {
    val modelUrl = "https://raw.githubusercontent.com/shubham0204/OnDevice-Face-Recognition-Android/2a9dd305081b9698d6b41af6a20ba28dc45e6846/app/src/main/assets/facenet.tflite"
    val expectedGitBlobSha = "8254aabae5cc73b8d2c15e7c589730eb3c264b87"
    val outputFile = layout.buildDirectory.file("generated/facenet-assets/facenet.tflite").get().asFile

    outputs.file(outputFile)

    doLast {
        if (
            outputFile.exists() &&
            FaceNetModelIntegrity.gitBlobSha(outputFile) == expectedGitBlobSha
        ) {
            return@doLast
        }

        outputFile.parentFile.mkdirs()
        val temporary = File(outputFile.parentFile, "facenet.tflite.part")
        if (temporary.exists()) temporary.delete()

        val connection = URI(modelUrl).toURL().openConnection().apply {
            connectTimeout = 20_000
            readTimeout = 120_000
        }
        connection.getInputStream().use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }

        check(FaceNetModelIntegrity.gitBlobSha(temporary) == expectedGitBlobSha) {
            "FaceNet model integrity check failed"
        }

        if (outputFile.exists()) outputFile.delete()
        check(temporary.renameTo(outputFile)) { "Unable to stage FaceNet model" }
    }
}

android {
    namespace = "com.hungphat.attendance"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.hungphat.attendance"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "CORE_API_BASE_URL", "\"https://hung-phat-945da1547594.herokuapp.com\"")
        buildConfigField("String", "ATTENDANCE_SOURCE_APP", "\"attendance-android\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("main") {
            assets.srcDir(layout.buildDirectory.dir("generated/facenet-assets"))
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareFaceNetModel)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.mlkit.face.detection)
    implementation(libs.litert)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
