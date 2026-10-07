// Google Play In-App Updates for both apps. Proprietary, so only the apps' `google` flavors depend
// on this module (googleImplementation); nothing here may reach an fdroid classpath, which the
// F-Droid verify scripts check.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kover)
}

val sharedDomainModule = ":shared:domain"

kotlin {
    androidTarget()

    sourceSets {
        androidMain.dependencies {
            implementation(project(sharedDomainModule))
            implementation(libs.play.app.update)
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.logging.kermit)
        }
        androidUnitTest.dependencies {
            implementation(libs.kotlin.test.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.junit)
        }
    }
}

android {
    namespace = "network.bisq.mobile.play_update"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
    testOptions {
        // As in shared:presentation: since AGP 9 a library's unit tests would target the compile
        // SDK, which is newer than the highest SDK Robolectric supports.
        targetSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        unitTests.isIncludeAndroidResources = true
    }
}
