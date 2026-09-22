plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vishala.kittentts"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.vishala.kittentts"

        minSdk = 23
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {

        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }

        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(
                org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
            )
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }

        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(
                "src/main/assets"
            )

            jniLibs.srcDir(
                "src/main/jniLibs"
            )
        }
    }
}

dependencies {

    implementation(
        "androidx.core:core-ktx:1.17.0"
    )

    implementation(
        "androidx.appcompat:appcompat:1.7.1"
    )
}
