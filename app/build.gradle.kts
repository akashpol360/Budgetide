import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Reads signing credentials from keystore.properties (gitignored, project root).
// See keystore.properties.template for the format - copy it, fill in your
// real keystore password/alias/key password, and save it as keystore.properties.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasSigningConfig = keystorePropertiesFile.exists()
if (hasSigningConfig) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.budgetide.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.budgetide.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.9.1"
    }

    if (hasSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // ✅ FIX: Add JVM compatibility settings
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ✅ FIX: Add Kotlin JVM target
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Works around a crash in "Lint Vital" (which auto-runs before every
    // release build) when Gradle itself is executed with a JDK newer than
    // this AGP/Lint version understands (e.g. JDK 25) - the analyzer's
    // version parser throws IllegalArgumentException on the version string
    // before lint even looks at any code. This disables that automatic
    // release-build lint pass; it does not affect app behavior or the
    // release build's correctness. The real fix is to run Gradle itself on
    // an older JDK (17 or 21) via Android Studio's Gradle JDK setting.
    lint {
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// ✅ FIX: Add JVM toolchain (recommended approach)
kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // One-time "Budgetide Pro" purchase via Google Play Billing.
    // Requires a matching one-time (INAPP) product to be created in Play
    // Console with the exact same product ID used in BillingManager.kt.
    implementation("com.android.billingclient:billing-ktx:7.1.1")
}
