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
        minSdk = 27
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
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
            isMinifyEnabled = false
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
            "META-INF/*.kotlin_module",
        )
    }

    val generatedAssetsDir = file("${layout.buildDirectory.get().asFile.path}/generated/assets/sourceZip")

    sourceSets {
        getByName("main") {
            assets.directories.add("src/main/assets")
            assets.directories.add(generatedAssetsDir.path)
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        warningsAsErrors = false
        // ComposableNaming is intentionally retained: this codebase has adopted lowerCamelCase
        // composable names as its established convention across ~85 declarations and hundreds of
        // call sites. Renaming them is tracked separately as a deliberate refactor.
        disable += setOf(
            "ComposableNaming",
            "ScopedStorage",
            "QueryAllPackagesPermission",
            "ProtectedPermissions"
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

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
    dependsOn(bundleProjectSource)
}
