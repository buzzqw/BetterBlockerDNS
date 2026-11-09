
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

// Release signing: set these environment variables for signed release builds
// (scripts/build_release.sh loads them from ~/.config/bbdns/release.env).
// External builders such as F-Droid leave them unset and keep the debug
// signing config; F-Droid re-signs the APK with its own key anyway.
val bbdnsKeystorePath = providers.environmentVariable("BBDNS_KEYSTORE")
    .orNull?.trim()?.takeIf { it.isNotEmpty() }
val bbdnsKeystorePassword = providers.environmentVariable("BBDNS_KEYSTORE_PASSWORD")
    .orNull?.takeIf { it.isNotEmpty() }
val bbdnsKeyAlias = providers.environmentVariable("BBDNS_KEY_ALIAS")
    .orNull?.trim()?.takeIf { it.isNotEmpty() }
val bbdnsKeyPassword = providers.environmentVariable("BBDNS_KEY_PASSWORD")
    .orNull?.takeIf { it.isNotEmpty() } ?: bbdnsKeystorePassword
val bbdnsHasReleaseSigning = bbdnsKeystorePath != null &&
    bbdnsKeystorePassword != null &&
    bbdnsKeyAlias != null
if (bbdnsHasReleaseSigning && !file(bbdnsKeystorePath!!).exists()) {
    throw GradleException("BBDNS_KEYSTORE not found: $bbdnsKeystorePath")
}

android {
    namespace = "com.buzzqw.bbdns"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.buzzqw.bbdns"
        minSdk = 24
        targetSdk = 36
        versionCode = bbdnsVersionCode
        versionName = bbdnsVersionName

        vectorDrawables {
            useSupportLibrary = true
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

    signingConfigs {
        if (bbdnsHasReleaseSigning) {
            create("release") {
                storeFile = file(bbdnsKeystorePath!!)
                storePassword = bbdnsKeystorePassword
                keyAlias = bbdnsKeyAlias
                keyPassword = bbdnsKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (bbdnsHasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// AGP 9.5 builds Compose Preview metadata for every variant and expects unit
// tests to package Android resources. Keep them enabled for all variants.
androidComponents {
    beforeVariants(selector().all()) { variant ->
        runCatching {
            variant.javaClass
                .getMethod("setEnableUnitTest", Boolean::class.javaPrimitiveType)
                .invoke(variant, true)
        }
    }
}

// Build the per-app language list from the translated resource folders:
// adding values-<locale>/strings.xml is enough to expose a new language in the
// picker and in Android's system settings.
val generatedLocaleConfigDir = layout.buildDirectory.dir("generated/localeConfig/res")

abstract class GenerateLocaleConfigTask : DefaultTask() {
    @get:InputDirectory
    abstract val resDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val qualifiers = resDir.get().asFile.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && it.name.startsWith("values-") }
            ?.map { it.name.removePrefix("values-") }
            ?.filter { it.matches(Regex("""^[a-z]{2,3}(-r[A-Z]{2})?$""")) }
            ?.toSortedSet()
            ?.toMutableList()
            ?: mutableListOf()
        qualifiers.add(0, "en")

        val xmlDir = outputDir.get().asFile.resolve("xml")
        xmlDir.mkdirs()
        xmlDir.resolve("locales_config.xml").writeText(
            buildString {
                appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                appendLine("""<locale-config xmlns:android="http://schemas.android.com/apk/res/android">""")
                qualifiers.distinct().forEach { qualifier ->
                    appendLine("""    <locale android:name="$qualifier" />""")
                }
                appendLine("</locale-config>")
            }
        )
    }
}

val generateLocaleConfig = tasks.register<GenerateLocaleConfigTask>("generateLocaleConfig") {
    resDir.set(layout.projectDirectory.dir("src/main/res"))
    outputDir.set(generatedLocaleConfigDir)
}
android.sourceSets.getByName("main").res.srcDir(generatedLocaleConfigDir.get().asFile)
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(generateLocaleConfig)
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

    // HTTP client (DNS over HTTPS and blocklist downloads)
    implementation(libs.okhttp)

    // Notification
    implementation(libs.androidx.core)

    //Fingerprint
    implementation(libs.androidx.biometric)

    // DNS Blocking
    implementation(libs.atomicfu)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.dnsjava)

}

// Hilt 2.57 validates Kotlin metadata with kotlin-metadata-jvm 2.2.x, which
// cannot read the 2.4 metadata emitted by Kotlin 2.4. Keep the annotation
// processor's metadata reader in sync with the compiler. This only affects the
// build-time processor classpath; the library is not packaged in the APK.
configurations.matching { it.name.startsWith("hiltAnnotationProcessor") }.configureEach {
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.21")
}

// Email the package after a release build, using scripts/email_latest_build.sh.
// This is opt-in so CI builds never try to send mail: set BBDNS_EMAIL_BUILD=1
// (scripts/build_release.sh does it). Delivery is best-effort: a failure does
// not fail the build.
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

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("emailReleaseBuild")
}

// Compatibility for external builders that still invoke the old flavor task
// (for example a pending IzzyOnDroid request). They reuse the single APK
// instead of building a second variant, keeping the historical output path.
tasks.register<Copy>("assembleFdroidRelease") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(layout.buildDirectory.dir("outputs/apk/fdroid/release"))
    rename { "app-fdroid-release.apk" }
}

tasks.register<Copy>("assembleFdroidDebug") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(layout.buildDirectory.dir("outputs/apk/fdroid/debug"))
    rename { "app-fdroid-debug.apk" }
}

// Advance version.properties to the next 1.0.<n> release. Run it before the
// release commit, then tag that commit (for example v1.0.23).
tasks.register<Exec>("bumpVersion") {
    group = "distribution"
    description = "Advance version.properties to the next 1.0.<n> release"
    workingDir = rootDir
    commandLine("bash", "scripts/bump_version.sh")
}
