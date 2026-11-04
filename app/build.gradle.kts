plugins {
    id("com.android.application")
}

android {
    namespace = "com.antutu.ABenchMark"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.antutu.ABenchMark"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // Versions recovered from the original APK's META-INF/*.version records.
    implementation("androidx.activity:activity:1.13.0")

    implementation("androidx.annotation:annotation-experimental:1.4.1")
    implementation("androidx.arch.core:core-runtime:2.2.0")
    implementation("androidx.core:core-viewtree:1.0.0")
    implementation("androidx.graphics:graphics-path:1.0.1")
    implementation("androidx.print:print:1.0.0")
    implementation("androidx.transition:transition:1.6.0")
    implementation("androidx.compose.runtime:runtime-retain:1.11.0")
    implementation("androidx.compose.ui:ui-tooling-data:1.10.4")
    implementation("androidx.core:core:1.18.0")
    implementation("androidx.core:core-ktx:1.18.0")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.animation:animation:1.10.4")
    implementation("androidx.compose.animation:animation-core:1.10.4")
    implementation("androidx.compose.foundation:foundation:1.10.4")
    implementation("androidx.compose.foundation:foundation-layout:1.10.4")
    implementation("androidx.compose.material:material:1.10.4")
    implementation("androidx.compose.material:material-ripple:1.10.4")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.runtime:runtime:1.11.0")
    implementation("androidx.compose.runtime:runtime-annotation:1.11.0")
    implementation("androidx.compose.runtime:runtime-saveable:1.11.0")
    implementation("androidx.compose.ui:ui:1.10.4")
    implementation("androidx.compose.ui:ui-geometry:1.10.4")
    implementation("androidx.compose.ui:ui-graphics:1.10.4")
    implementation("androidx.compose.ui:ui-text:1.10.4")
    implementation("androidx.compose.ui:ui-unit:1.10.4")
    implementation("androidx.compose.ui:ui-util:1.10.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.4")

    implementation("androidx.lifecycle:lifecycle-livedata:2.10.0")
    implementation("androidx.lifecycle:lifecycle-livedata-core:2.10.0")
    implementation("androidx.lifecycle:lifecycle-livedata-core-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0")

    implementation("androidx.savedstate:savedstate:1.4.0")
    implementation("androidx.savedstate:savedstate-ktx:1.4.0")
    implementation("androidx.savedstate:savedstate-compose:1.4.0")
    implementation("androidx.navigationevent:navigationevent:1.0.0")
    implementation("androidx.navigationevent:navigationevent-compose:1.0.0")

    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("androidx.window:window:1.5.0")
    implementation("androidx.window:window-core:1.5.0")
    implementation("androidx.tracing:tracing:1.2.0")
    implementation("androidx.versionedparcelable:versionedparcelable:1.1.1")
    implementation("androidx.documentfile:documentfile:1.0.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("androidx.interpolator:interpolator:1.0.0")
    implementation("androidx.legacy:legacy-support-core-utils:1.0.0")
    implementation("androidx.loader:loader:1.0.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.0.0")
    implementation("androidx.customview:customview-poolingcontainer:1.0.0")
    implementation("androidx.autofill:autofill:1.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.10.4")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.4")
}
