import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.jmd.helper"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jmd.helper"
        minSdk = 27
        targetSdk = 37
        versionCode = 4
        versionName = "1.3.0"
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    var hasValidReleaseKeystore = false
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
    }

    signingConfigs {
        create("release") {
            val ksFileName = keystoreProperties.getProperty("storeFile")
            val ksFile = if (!ksFileName.isNullOrBlank()) {
                val moduleFile = file(ksFileName)
                if (moduleFile.exists()) moduleFile else rootProject.file(ksFileName)
            } else null

            if ((ksFile != null) && ksFile.exists()) {
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
                    hasValidReleaseKeystore = true
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasValidReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            if (hasValidReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<JavaCompile>().configureEach {
        enabled = false
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
            "META-INF/*.kotlin_module",
        )
    }

    val generatedAssetsDir = file("${layout.buildDirectory.get().asFile.path}/generated/assets/sourceZip")
    val generatedSourceTreeDir = file("${layout.buildDirectory.get().asFile.path}/generated/assets/sourceTree")

    sourceSets {
        getByName("main") {
            assets.directories.add("src/main/assets")
            assets.directories.add(generatedAssetsDir.path)
            assets.directories.add(generatedSourceTreeDir.path)
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
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

    // 1. Architecture & Persistence: DataStore, Serialization, Room Runtime
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // 2. Background Execution, Startup & WorkManager
    implementation("androidx.startup:startup-runtime:1.2.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.3.0")

    // 3. UI, Media & Compose Performance
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.window:window:1.5.1")
    implementation("androidx.tracing:tracing-ktx:1.3.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")

    // 4. Security & Biometrics
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.security:security-crypto:1.1.0")

    // 5. Reliability & Debugging
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // 6. Enhanced Testing
    testImplementation("io.mockk:mockk:1.13.14")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

val bundleProjectSource = tasks.register<Zip>("bundleProjectSource") {
    description = "Bundles project source files into a ZIP asset for build reproducibility"
    group = "build"
    archiveFileName.set("project_source.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/assets/sourceZip"))
    from(rootProject.projectDir) {
        include("build.gradle.kts")
        include("settings.gradle.kts")
        include("gradle.properties")
        include("gradlew")
        include("gradlew.bat")
        include("gradle/wrapper/**")
        include("app/build.gradle.kts")
        include("app/proguard-rules.pro")
        include("app/src/**")
        include("*.md")
        include("docs/**")
        include(".editorconfig")
        include(".gitignore")
        exclude("keystore.properties")
        exclude("app/keystore.properties")
        exclude("app/release-key.jks")
        exclude("**/*.jks")
        exclude("**/*.keystore")
        exclude("**/local.properties")
        exclude("**/.env*")
        exclude("**/build/**")
        exclude("**/.gradle/**")
        exclude("**/.kotlin/**")
        exclude("**/project_source.zip")
        exclude("**/*.apk")
    }
}

val bundleProjectSourceFolder = tasks.register<Sync>("bundleProjectSourceFolder") {
    description = "Copies the complete project source tree into an assets folder for in-APK source extraction"
    group = "build"
    into(layout.buildDirectory.dir("generated/assets/sourceTree/codebase"))
    from(rootProject.projectDir) {
        include("build.gradle.kts")
        include("settings.gradle.kts")
        include("gradle.properties")
        include("gradlew")
        include("gradlew.bat")
        include("gradle/wrapper/**")
        include("app/build.gradle.kts")
        include("app/proguard-rules.pro")
        include("app/src/**")
        include("*.md")
        include("docs/**")
        include(".editorconfig")
        include(".gitignore")
        exclude("keystore.properties")
        exclude("app/keystore.properties")
        exclude("app/release-key.jks")
        exclude("**/*.jks")
        exclude("**/*.keystore")
        exclude("**/local.properties")
        exclude("**/.env*")
        exclude("**/build/**")
        exclude("**/.gradle/**")
        exclude("**/.kotlin/**")
        exclude("**/project_source.zip")
        exclude("**/*.apk")
    }
}

val copyReleaseApkToRoot = tasks.register("copyReleaseApkToRoot") {
    description = "Copies the assembled signed release APK to the project root directory"
    group = "build"
    doNotTrackState("Destination is root project directory containing locked Gradle daemon files")
    doLast {
        val srcApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        if (srcApk.exists()) {
            val destApk = rootProject.file("app-release.apk")
            srcApk.copyTo(destApk, overwrite = true)
            logger.lifecycle("Successfully copied signed release APK to: ${destApk.absolutePath}")
        }
    }
}

afterEvaluate {
    tasks.findByName("assembleRelease")?.finalizedBy(copyReleaseApkToRoot)
}

tasks.named("preBuild") {
    dependsOn(bundleProjectSource, bundleProjectSourceFolder)
}
