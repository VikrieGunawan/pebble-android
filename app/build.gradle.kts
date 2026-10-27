import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pebble.player"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pebble.player"
        minSdk = 29          // Android 10
        targetSdk = 36       // Android 16
        versionCode = 4
        versionName = "1.0.4-beta"
    }

    // One fixed key for every build, so updates install over the old version.
    signingConfigs {
        create("pebble") {
            storeFile = file("pebble.jks")
            storePassword = "pebble123"
            keyAlias = "pebble"
            keyPassword = "pebble123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("pebble")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Two APKs per build: one for 64-bit phones (arm64-v8a) and one for 32-bit phones (armeabi-v7a).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // These prebuilt libs have no symbols to strip; keep them as-is instead of warning.
        jniLibs.keepDebugSymbols += setOf("**/libandroidx.graphics.path.so", "**/libdatastore_shared_counter.so")
    }
}

// Release only: the debug variant is switched off.
androidComponents {
    beforeVariants { variant -> if (variant.buildType == "debug") variant.enable = false }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.animation.ExperimentalAnimationApi",
        )
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
}
