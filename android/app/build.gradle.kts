import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Cargar local.properties explicitamente. Gradle no la lee automaticamente
// (solo lee gradle.properties), por eso necesitamos hacerlo aqui. Las claves
// sensibles (Maps API key) viven aqui porque local.properties esta gitignored.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        FileInputStream(file).use { load(it) }
    }
}

android {
    namespace = "com.pacemdeus.bodas"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.pacemdeus.bodas"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google Maps API key (HU-04). Se lee de local.properties (gitignored).
        // Si la propiedad MAPS_API_KEY no existe ahi, queda vacia y la app
        // detecta que no esta configurada (isMapsApiKeyConfigured) y cae al
        // fallback de mostrar solo coordenadas.
        manifestPlaceholders["MAPS_API_KEY"] =
            (localProperties.getProperty("MAPS_API_KEY") ?: "")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Networking - Volley (el patron del profesor)
    implementation("com.android.volley:volley:1.2.1")

    // Sprint 4 - CameraX (HU-03 foto del local)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Sprint 4 - Google Maps SDK + Compose wrapper (HU-04)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    // Sprint 5 - Coil para cargar la foto del local desde S3 publico
    implementation(libs.coil.compose)

    // Firebase Cloud Messaging para push notifications (v07).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
