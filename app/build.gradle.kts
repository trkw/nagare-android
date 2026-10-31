import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Firebase settings read from app/google-services.json, which stays out of git.
 * Without the file the app builds and runs with push notifications off.
 * Read here instead of with the google-services plugin, so a missing file does not break the build.
 */
val firebase: Map<String, String> = run {
    val file = file("google-services.json")
    if (!file.exists()) return@run emptyMap()
    @Suppress("UNCHECKED_CAST")
    val root = JsonSlurper().parse(file) as Map<String, Any?>
    val project = root["project_info"] as Map<String, Any?>
    val client = (root["client"] as List<Map<String, Any?>>).first {
        val info = it["client_info"] as Map<String, Any?>
        (info["android_client_info"] as Map<String, Any?>)["package_name"] == "dev.trkw.nagare"
    }
    mapOf(
        "APP_ID" to (client["client_info"] as Map<String, Any?>)["mobilesdk_app_id"] as String,
        "API_KEY" to ((client["api_key"] as List<Map<String, Any?>>).first()["current_key"] as String),
        "PROJECT_ID" to project["project_id"] as String,
        "SENDER_ID" to project["project_number"] as String,
    )
}

android {
    namespace = "dev.trkw.nagare"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.trkw.nagare"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        for (key in listOf("APP_ID", "API_KEY", "PROJECT_ID", "SENDER_ID")) {
            buildConfigField("String", "FIREBASE_$key", "\"${firebase[key] ?: ""}\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    debugImplementation(libs.compose.ui.tooling)
}
