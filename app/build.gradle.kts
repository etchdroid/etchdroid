@file:Suppress("UnstableApiUsage")

val isGPlayFlavor = gradle.startParameter.taskNames.any { it.lowercase().contains("gplay") }
System.setProperty("etchdroid.isGPlayFlavor", isGPlayFlavor.toString())

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.junit5)

    if (System.getProperty("etchdroid.isGPlayFlavor") == "true" || System.getenv("ETCHDROID_ENABLE_SENTRY") == "true") {
        println("EtchDroid: Sentry enabled")
//        alias(libs.plugins.gplay.sentry)
        alias(libs.plugins.gplay.sentry.kotlin)
    } else {
        println("EtchDroid: Sentry not enabled")
    }
}

android {
    namespace = "eu.depau.etchdroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.depau.etchdroid"
        minSdk = 21
        targetSdk = 35
        versionCode = 24
        versionName = "1.9.rc4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isPseudoLocalesEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro",
            )
        }
    }
    flavorDimensions += "store"
    productFlavors {
        create("foss") {
            isDefault = true
            dimension = "store"
        }
        create("gplay") {
            dimension = "store"
        }
    }
    packaging {
        resources {
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
            excludes += "META-INF/licenses/ASM"
            excludes += "META-INF/libaums_release.kotlin_module"
            excludes += "win32-x86/attach_hotspot_windows.dll"
            excludes += "win32-x86-64/attach_hotspot_windows.dll"
        }
    }
    compileOptions {
        encoding = "UTF-8"
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.maxHeapSize = "4g"
        }
    }
}

dependencies {
    implementation(libs.accompanist.navigation.animation)
    implementation(libs.accompanist.permissions)
    implementation(libs.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.constraintlayout.compose)
    implementation(libs.core.ktx)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlinx.coroutines.debug)
    implementation(libs.libaums.core)
    implementation(libs.libaums.libusbcommunication)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.service)
    implementation(libs.lottie.compose)
    implementation(libs.material)
    implementation(libs.material.icons.extended)
    implementation(libs.material3)
    implementation(libs.material3.adaptive)
    implementation(platform(libs.compose.bom))

    "gplayImplementation"(libs.gplay.review)
    "gplayImplementation"(libs.gplay.review.ktx)
    "gplayImplementation"(libs.gplay.sentry.android)
    "gplayImplementation"(libs.gplay.sentry.compose)

    debugImplementation(libs.compose.ui.test.manifest)
    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.test.core)
}
