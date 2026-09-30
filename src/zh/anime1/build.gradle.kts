import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
}

val extName = "Anime1.me"
val extClass = ".Anime1"
val extVersionCode = 5
val isNsfw = false

// Aniyomi reads the extensions-lib version from the major part of versionName.
val libVersion = "14"
val pkgSuffix = "${project.parent!!.name}.${project.name}"

android {
    namespace = "eu.kanade.tachiyomi.animeextension"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.animeextension.$pkgSuffix"
        minSdk = 21
        targetSdk = 34
        versionCode = extVersionCode
        versionName = "$libVersion.$extVersionCode"
        manifestPlaceholders += mapOf(
            "appName" to "Aniyomi: $extName",
            "extClass" to extClass,
            "nsfw" to if (isNsfw) 1 else 0,
        )
    }

    sourceSets["main"].apply {
        manifest.srcFile("AndroidManifest.xml")
        java.setSrcDirs(listOf("src"))
        res.setSrcDirs(listOf("res"))
    }

    val keystore = rootProject.file("signingkey.jks")
    signingConfigs {
        create("release") {
            storeFile = keystore
            storePassword = System.getenv("KEY_STORE_PASSWORD")
            keyAlias = System.getenv("ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // Without a keystore (local builds), fall back to the debug key so the APK still installs.
            signingConfig = signingConfigs.getByName(if (keystore.exists()) "release" else "debug")
            isMinifyEnabled = false
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        aidl = false
        renderScript = false
        resValues = false
        shaders = false
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

base {
    archivesName.set("aniyomi-$pkgSuffix-v$libVersion.$extVersionCode")
}

dependencies {
    compileOnly(libs.bundles.provided)
    implementation(libs.opencc4j)
}
