plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

// ✅ FIX: Add JVM toolchain configuration for all subprojects
allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
}

// ✅ FIX: Alternative/Additional - Configure KSP to use same JVM target
subprojects {
    plugins.withId("com.google.devtools.ksp") {
        tasks.withType<com.google.devtools.ksp.gradle.KspTaskJvm>().configureEach {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
}