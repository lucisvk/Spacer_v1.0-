import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "2.0.21"
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

fun localProp(name: String, fallback: String = ""): String {
    return localProperties.getProperty(name, fallback)
}

fun secret(name: String): String {
    // Resolution order:
    // 1) local.properties
    // 2) Gradle property (-P or gradle.properties)
    // 3) environment variable
    return localProp(name).ifBlank {
        (findProperty(name) as? String).orEmpty().ifBlank {
            System.getenv(name).orEmpty()
        }
    }
}

fun asBuildConfigString(value: String): String {
    return "\"${value.replace("\"", "\\\"")}\""
}

android {
    namespace = "com.example.spacer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.spacer"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            asBuildConfigString(secret("SUPABASE_URL"))
        )

        buildConfigField(
            "String",
            "SUPABASE_KEY",
            asBuildConfigString(secret("SUPABASE_KEY"))
        )

        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            asBuildConfigString(secret("GOOGLE_WEB_CLIENT_ID"))
        )

        buildConfigField(
            "String",
            "DISCORD_CLIENT_ID",
            asBuildConfigString(secret("DISCORD_CLIENT_ID"))
        )

        // Google Cloud: enable "Places API (New)" and restrict key to Android + Places.
        buildConfigField(
            "String",
            "PLACES_API_KEY",
            asBuildConfigString(secret("PLACES_API_KEY"))
        )

        buildConfigField(
            "String",
            "GROQ_API_KEY",
            asBuildConfigString(secret("GROQ_API_KEY"))
        )
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
        buildConfig = true
    }
}

dependencies {
    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.6"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-android:3.0.3")
    implementation("io.ktor:ktor-client-websockets:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Used by Supabase Auth OAuth (Custom Tabs).
    implementation("androidx.browser:browser:1.8.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}