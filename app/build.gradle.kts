import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jmd.autotext"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jmd.autotext"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
    }

    signingConfigs {
        create("release") {
            val ksFileName = keystoreProperties.getProperty("storeFile")
            val ksFile = if (!ksFileName.isNullOrBlank()) file(ksFileName) else null
            if (ksFile != null && ksFile.exists()) {
                val storePass = keystoreProperties.getProperty("storePassword")
                val kAlias = keystoreProperties.getProperty("keyAlias")
                val keyPass = keystoreProperties.getProperty("keyPassword")
                if (!storePass.isNullOrBlank() && !kAlias.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                    storeFile = ksFile
                    storePassword = storePass
                    keyAlias = kAlias
                    keyPassword = keyPass
                    enableV1Signing = true
                    enableV2Signing = true
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/*.kotlin_module"
        )
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += "UnusedResources"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
