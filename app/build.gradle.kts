
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

// ---------------------------------------------------------------------------
// Version: stored in version.properties so every build from a commit (local,
// CI and F-Droid) produces the same versionName/versionCode. Use
// `./gradlew bumpVersion` before a release commit, then tag the commit.
// Nightly CI can still override the build number with BBDNS_BUILD_NUMBER.
// ---------------------------------------------------------------------------
val bbdnsVersionFile = rootProject.layout.projectDirectory.file("version.properties")
val bbdnsVersionText = providers.fileContents(bbdnsVersionFile).asText.orNull

fun bbdnsVersionValue(key: String): String? = bbdnsVersionText
    ?.lineSequence()
    ?.map { it.trim() }
    ?.firstOrNull { it.startsWith("$key=") }
    ?.substringAfter('=')
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

val bbdnsEnvBuildNumber = providers.environmentVariable("BBDNS_BUILD_NUMBER")
    .orNull?.trim()?.toIntOrNull()?.takeIf { it > 0 }
val bbdnsVersionCode = bbdnsEnvBuildNumber?.let { 1_000_000 + it }
    ?: bbdnsVersionValue("versionCode")?.toIntOrNull()
    ?: 1_000_001
val bbdnsVersionName = bbdnsEnvBuildNumber?.let { "1.0.$it" }
    ?: bbdnsVersionValue("versionName")
    ?: "1.0.1"

android {
    namespace = "com.buzzqw.bbdns"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.buzzqw.bbdns"
        minSdk = 24
        targetSdk = 36
        versionCode = bbdnsVersionCode
        versionName = bbdnsVersionName

        vectorDrawables {
            useSupportLibrary = true
        }
        androidResources {
            // Shipped languages: English (default), Italian, German, French and Spanish.
            localeFilters += listOf("en", "it", "de", "fr", "es")
        }
        externalNativeBuild {
            cmake {
                cppFlags += ""
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    flavorDimensions += "store"
    productFlavors {
        create("playstore") {
            dimension = "store"
        }
        create("fdroid") {
            dimension = "store"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
        }
    }
    
    tasks.whenTaskAdded {
        if (name.startsWith("merge") && name.contains("JniLibFolders")) {
            dependsOn("buildGoLibraries")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE.md",
                "/META-INF/README.md",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST"
            )
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs/")
        }
    }
    
    tasks.register<Exec>("buildGoLibraries") {
        group = "build"
        description = "Build Go libraries for Android"
        
        val goDir = layout.projectDirectory.dir("src/main/go")
        val libsDir = layout.projectDirectory.dir("libs")
        
        inputs.dir(goDir)
        outputs.files(
            libsDir.file("arm64-v8a/libnflog.so"),
            libsDir.file("armeabi-v7a/libnflog.so")
        )
        outputs.upToDateWhen { false }
        
        workingDir(goDir)
        
        val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
        val buildScript = if (isWindows) "build-android.bat" else "build-android.sh"
        
        if (isWindows) {
            commandLine("cmd", "/c", buildScript)
        } else {
            commandLine("bash", buildScript)
        }
        
        doFirst {
            val buildScriptFile = goDir.file(buildScript).asFile
            if (!buildScriptFile.exists()) {
                throw GradleException("Build script $buildScript not found in ${goDir.asFile}")
            }
            
            libsDir.dir("arm64-v8a").asFile.mkdirs()
            libsDir.dir("armeabi-v7a").asFile.mkdirs()
        }
    }
    ksp {
        arg("dagger.fastInit", "enabled")
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(libs.m3color)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.common)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.compile)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    // Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // Compose
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)

    // AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.converter.scalars)

    // Notification
    implementation(libs.androidx.core)

    //Fingerprint
    implementation(libs.androidx.biometric)

    // DNS Blocking
    implementation(libs.atomicfu)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.dnsjava)
    implementation(libs.pcap4j.core)

}

// Email the package after a release build, using scripts/email_latest_build.sh.
// This is opt-in so external builders such as F-Droid never try to send mail:
// set BBDNS_EMAIL_BUILD=1 (scripts/build_release.sh does it). Delivery is
// best-effort: a failure does not fail the build.
tasks.register<Exec>("emailReleaseBuild") {
    group = "distribution"
    description = "Email the release APK produced by this build"
    workingDir = rootDir
    commandLine("bash", "scripts/email_latest_build.sh")
    isIgnoreExitValue = true
    onlyIf {
        System.getenv("BBDNS_EMAIL_BUILD") == "1" &&
            System.getenv("BBDNS_SKIP_EMAIL") != "1"
    }
}

tasks.matching { it.name == "assembleFdroidRelease" }.configureEach {
    finalizedBy("emailReleaseBuild")
}

// Advance version.properties to the next 1.0.<n> release. Run it before the
// release commit, then tag that commit (for example v1.0.23).
tasks.register<Exec>("bumpVersion") {
    group = "distribution"
    description = "Advance version.properties to the next 1.0.<n> release"
    workingDir = rootDir
    commandLine("bash", "scripts/bump_version.sh")
}
