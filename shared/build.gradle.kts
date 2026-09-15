plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val isXcodeEnvironment = providers.environmentVariable("XCODE_VERSION_ACTUAL").isPresent || 
                          providers.environmentVariable("SDK_NAME").isPresent

val isXcodeAvailable = providers.gradleProperty("ios.enabled").map { it.toBoolean() }.getOrElse(false) || 
                       isXcodeEnvironment

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    if (isXcodeAvailable) {
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "shared"
                isStatic = true
            }
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.materialIconsExtended)
            
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.room.runtime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.spotify.auth)
            implementation(libs.gson)
            implementation(libs.retrofit)
            implementation(libs.retrofit.converter.gson)
            
            // For the Spotify App Remote AAR
            compileOnly(fileTree(mapOf("dir" to "../app/libs", "include" to listOf("*.aar", "*.jar"))))
        }
        iosMain.dependencies {}
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.example.runningbeat.shared"
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    if (isXcodeAvailable) {
        add("kspIosX64", libs.androidx.room.compiler)
        add("kspIosArm64", libs.androidx.room.compiler)
        add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    }
}

android {
    namespace = "com.example.runningbeat.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
