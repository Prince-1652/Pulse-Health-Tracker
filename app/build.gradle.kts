plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.pulse.tracker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pulse.tracker"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs("-Xmx3072m", "-XX:+UseG1GC")
                it.maxHeapSize = "3072m"
            }
        }
    }
}

dependencies {
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.fragment)
    implementation(libs.recyclerview)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.mpandroidchart)
    implementation(libs.media)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.core.testing)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
