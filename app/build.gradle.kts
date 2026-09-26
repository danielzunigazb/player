import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing key, from environment variables (CI secrets) or an untracked
 * keystore.properties at the repo root. Never committed: see README → "Firma".
 */
val releaseSigning: Map<String, String>? = run {
    val props = Properties()
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use(props::load)
    fun value(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: props.getProperty(prop)
    val values = mapOf(
        "storeFile" to value("PLAYER_KEYSTORE_FILE", "storeFile"),
        "storePassword" to value("PLAYER_KEYSTORE_PASSWORD", "storePassword"),
        "keyAlias" to value("PLAYER_KEY_ALIAS", "keyAlias"),
        "keyPassword" to value("PLAYER_KEY_PASSWORD", "keyPassword"),
    )
    if (values.values.all { it != null }) values.mapValues { it.value!! } else null
}

/** Set on tagged/stable builds so a missing key fails the build instead of silently using debug. */
val requireReleaseKey = System.getenv("PLAYER_REQUIRE_RELEASE_KEY") == "true"

android {
    namespace = "com.danielzuniga.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.danielzuniga.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.4.2"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getValue("storeFile"))
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = when {
                releaseSigning != null -> signingConfigs.getByName("release")
                requireReleaseKey -> throw GradleException("PLAYER_REQUIRE_RELEASE_KEY is set but no release key is configured")
                // Local/dev builds without the key still produce an installable (debug-signed) APK.
                else -> signingConfigs.getByName("debug").also {
                    logger.warn("Release key not configured: signing the release APK with the debug key.")
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.maxHeapSize = "3g" }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.coil.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.reorderable)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
