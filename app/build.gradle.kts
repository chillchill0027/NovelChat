import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

fun evalProp(key: String): String? =
    providers.gradleProperty(key).orNull?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(key)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.novelchat"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.novelchat"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "2.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (providers.gradleProperty("novelchat.qaInstrumentation").orNull == "true" ||
            evalProp("novelchat.runEval").equals("true", ignoreCase = true)
        ) {
            listOf(
                "novelchat.runEval",
                "novelchat.evalApiKey",
                "novelchat.evalBaseUrl",
                "novelchat.evalModel",
                "novelchat.evalEngine",
                "novelchat.evalScenario"
            ).forEach { key ->
                evalProp(key)?.let { value ->
                    testInstrumentationRunnerArguments[key] = value
                }
            }
        }
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val releaseStorePath = evalProp("novelchat.storeFile")
    val releaseStorePassword = evalProp("novelchat.storePassword")
    val releaseKeyAlias = evalProp("novelchat.keyAlias")
    val releaseKeyPassword = evalProp("novelchat.keyPassword")
    val hasReleaseSigning = !releaseStorePath.isNullOrBlank() &&
        file(releaseStorePath).isFile &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".testbed"
            versionNameSuffix = "-testbed"
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    testBuildType = if (
        providers.gradleProperty("novelchat.qaInstrumentation").orNull == "true"
    ) {
        "qa"
    } else {
        "debug"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.material)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging.interceptor)

    // Gson
    implementation(libs.gson)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    "qaImplementation"(libs.androidx.compose.ui.tooling)
    "qaImplementation"(libs.androidx.compose.ui.test.manifest)
}
