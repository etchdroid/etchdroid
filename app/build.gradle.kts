@file:Suppress("UnstableApiUsage")

val isGPlayFlavor = gradle.startParameter.taskNames.any { it.lowercase().contains("gplay") }
System.setProperty("etchdroid.isGPlayFlavor", isGPlayFlavor.toString())

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.junit5)
    alias(libs.plugins.robolectric.junit5)

    if (System.getProperty("etchdroid.isGPlayFlavor") == "true" || System.getenv("ETCHDROID_ENABLE_SENTRY") == "true") {
        println("EtchDroid: Sentry and Crashlytics enabled")
        alias(libs.plugins.gplay.sentry.kotlin)
        alias(libs.plugins.google.gms.google.services)
        alias(libs.plugins.google.firebase.crashlytics)
    } else {
        println("EtchDroid: Sentry and Crashlytics not enabled")
    }
}

android {
    val sdkMin = 23
    val sdkTarget = 36

    namespace = "eu.depau.etchdroid"
    compileSdk = sdkTarget

    defaultConfig {
        applicationId = "eu.depau.etchdroid"
        minSdk = sdkMin
        targetSdk = sdkTarget
        versionCode = 25
        versionName = "2.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        unitTests.all {
            it.useJUnitPlatform()
            it.maxHeapSize = "4g"
            it.systemProperty("robolectric.dependency.proxy.host", project.findProperty("systemProp.https.proxyHost") ?: System.getenv("ROBOLECTRIC_PROXY_HOST"))
            it.systemProperty("robolectric.dependency.proxy.port", project.findProperty("systemProp.https.proxyPort") ?: System.getenv("ROBOLECTRIC_PROXY_PORT"))
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.accompanist.permissions)
    implementation(libs.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.constraintlayout.compose)
    implementation(libs.core.ktx)
    implementation(libs.kotlinx.coroutines.debug)
    implementation(libs.libaums.core)
    // TODO: re-enable once released
    // implementation(libs.libaums.libusbcommunication)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.service)
    implementation(libs.localbroadcastmanager)
    implementation(libs.lottie.compose)
    implementation(libs.material)
    implementation(libs.material.icons.extended)
    implementation(libs.material3)
    implementation(libs.material3.adaptive)
    implementation(libs.navigation.compose)
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
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)

    if (System.getProperty("etchdroid.isGPlayFlavor") == "true" || System.getenv("ETCHDROID_ENABLE_SENTRY") == "true") {
        implementation(libs.firebase.crashlytics)
    }
}
