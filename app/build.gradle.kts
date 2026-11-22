import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val runNumber: Int = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1

android {
    namespace = "com.willian.tvaberta"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.willian.tvaberta"
        minSdk = 26
        targetSdk = 34

        // Sobe a cada execução do GitHub Actions,
        // para o Android aceitar a atualização por cima.
        versionCode = maxOf(1, runNumber)
        versionName = "1.11.0"

        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm")
        fmt.timeZone = TimeZone.getTimeZone("America/Sao_Paulo")

        buildConfigField(
            "String",
            "BUILD_DATE",
            "\"" + fmt.format(Date()) + "\""
        )
    }

    signingConfigs {
        create("fixa") {
            storeFile = file("tvaberta.jks")
            storePassword = "tvaberta123"
            keyAlias = "tvaberta"
            keyPassword = "tvaberta123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixa")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")

    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Media3 / ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:1.4.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // HTTP
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Imagens
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
