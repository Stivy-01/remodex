import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}
val hasReleaseSigning =
    keystorePropertiesFile.exists() &&
        listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
            .all { key -> !keystoreProperties.getProperty(key).isNullOrBlank() }

fun betaConfigValue(name: String): String =
    (providers.gradleProperty(name).orNull ?: System.getenv(name) ?: "").trim()

fun quotedBuildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val betaApiBaseUrl = betaConfigValue("BETA_API_BASE_URL")
val betaApiKey = betaConfigValue("BETA_API_KEY")
val betaEnabled =
    betaConfigValue("BETA_ENABLED").equals("true", ignoreCase = true) &&
        betaApiBaseUrl.isNotBlank()

android {
    namespace = "com.remodex.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.remodex.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.1.2"
        buildConfigField("boolean", "BETA_ENABLED", betaEnabled.toString())
        buildConfigField("String", "BETA_API_BASE_URL", quotedBuildConfigString(betaApiBaseUrl))
        buildConfigField("String", "BETA_API_KEY", quotedBuildConfigString(betaApiKey))
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
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
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

gradle.taskGraph.whenReady {
    val releasePackagingTaskRequested =
        allTasks.any { task ->
            task.project == project &&
                task.name.endsWith("Release") &&
                (
                    task.name.startsWith("assemble") ||
                        task.name.startsWith("bundle") ||
                        task.name.startsWith("package")
                )
        }

    if (releasePackagingTaskRequested && !hasReleaseSigning) {
        throw GradleException("Missing android/key.properties for release signing")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    exclude("**/ui/design/**")
}

dependencies {
    // Mike Penz markdown 0.39.x targets Kotlin 2.2 / newer runtime APIs (e.g. Updater.init-impl); BOM 2025.10.x still pins runtime 1.9.4 → NoSuchMethodError.
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.composables:icons-lucide-android:2.2.1")
    implementation("com.valentinilk.shimmer:compose-shimmer:1.4.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0")

    val markdownRenderer = "0.39.2"
    implementation("com.mikepenz:multiplatform-markdown-renderer:$markdownRenderer")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:$markdownRenderer")
    implementation("com.mikepenz:multiplatform-markdown-renderer-code:$markdownRenderer")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
