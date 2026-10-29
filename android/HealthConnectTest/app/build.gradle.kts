import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val productionSigningPath = providers.environmentVariable("HEALTH_SIGNING_PROPERTIES").orNull
val productionSigning = productionSigningPath?.let { path ->
    Properties().apply { file(path).inputStream().use { load(it) } }
}

android {
    namespace = "com.uriel.healthconnecttest"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.uriel.healthconnecttest"
        minSdk = 28
        targetSdk = 36
        versionCode = 9
        versionName = "0.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (productionSigning != null) create("production") {
            storeFile = file(productionSigning.getProperty("storeFile"))
            storePassword = productionSigning.getProperty("storePassword")
            keyAlias = productionSigning.getProperty("keyAlias")
            keyPassword = productionSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            if (productionSigning != null) signingConfig = signingConfigs.getByName("production")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

tasks.configureEach {
    if (name in setOf("packageRelease", "bundleRelease")) doFirst {
        check(productionSigning != null) { "Set HEALTH_SIGNING_PROPERTIES to your private signing properties file before building a release." }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
