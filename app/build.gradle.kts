import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val releaseVersionCode = providers.gradleProperty("versionCode").orNull ?: appVersion.getProperty("versionCode")
val releaseVersionName = providers.gradleProperty("versionName").orNull ?: appVersion.getProperty("versionName")
require(releaseVersionCode.toIntOrNull()?.let { it in 1..2_100_000_000 } == true && releaseVersionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Versione non valida." }
val signingEnvironment = listOf("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
val hasReleaseKey = signingEnvironment.all { !System.getenv(it).isNullOrBlank() }

android {
    namespace = "cc.baldeddu.prezzi"
    compileSdk = 36
    defaultConfig {
        applicationId = "cc.baldeddu.prezzi"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode.toInt()
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"https://inflation.baldeddu.cc\"")
        buildConfigField("String", "RELEASE_REPOSITORY", "\"GoryNickel9/personal-inflation-android\"")
    }
    signingConfigs {
        if (hasReleaseKey) create("release") {
            storeFile = file(System.getenv("ANDROID_KEYSTORE_PATH"))
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst { check(hasReleaseKey) { "Per la release configura le quattro variabili ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS e ANDROID_KEY_PASSWORD." } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
