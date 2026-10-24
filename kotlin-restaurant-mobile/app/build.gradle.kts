import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val restroLocalProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { load(it) }
    }
}

val restroDebugApiUrl = providers.gradleProperty("restroDebugApiUrl").orNull
    ?: restroLocalProperties.getProperty("restro.debug.apiUrl")
    ?: "http://10.0.2.2:8080/api/delivery"

android {
    namespace = "com.restaurant.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.restaurant.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "API_URL", "\"" + (providers.gradleProperty("restroApiUrl").orNull ?: "https://localhost/api/delivery") + "\"" )
        val mapsKey = providers.gradleProperty("MAPS_API_KEY").orNull ?: restroLocalProperties.getProperty("MAPS_API_KEY") ?: System.getenv("MAPS_API_KEY") ?: ""
        val rzpKey = providers.gradleProperty("RAZORPAY_KEY_ID").orNull ?: restroLocalProperties.getProperty("RAZORPAY_KEY_ID") ?: System.getenv("RAZORPAY_KEY_ID") ?: ""
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsKey\"")
        buildConfigField("String", "RAZORPAY_KEY_ID", "\"$rzpKey\"")
        // Firebase is only treated as configured when google-services.json exists and does not
        // contain the known placeholder OAuth client IDs. A placeholder config makes every Firebase
        // call fail at runtime, so the app must not pretend phone auth / Firestore are available.
        val googleServicesFile = file("google-services.json")
        val googleServicesText = if (googleServicesFile.isFile) googleServicesFile.readText() else ""
        val firebaseConfigured = googleServicesText.isNotBlank() &&
            !googleServicesText.contains("abcdef1234567890") &&
            !googleServicesText.contains("webclient1234567890")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        // Online payments need a provider SDK plus a server holding the secret key that verifies
        // signatures/webhooks. Neither exists yet, so online prepaid checkout is disabled by default.
        val onlinePaymentsVerified = (providers.gradleProperty("restroOnlinePaymentsVerified").orNull ?: "false").toBoolean()
        buildConfigField("boolean", "ONLINE_PAYMENTS_VERIFIED", onlinePaymentsVerified.toString())
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_URL", "\"$restroDebugApiUrl\"" )
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INDEX/*"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Firebase BoM and SDKs
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")

    // Compose UI & Material 3
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Firebase BoM & SDKs
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        val endpoint = providers.gradleProperty("restroApiUrl").orNull
        require(endpoint != null && endpoint.startsWith("https://") && !endpoint.contains("localhost")) {
            "Release requires -PrestroApiUrl=https://your-server/api/delivery"
        }
    }
}

dependencies {
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
