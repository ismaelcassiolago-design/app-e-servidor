plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val appVersionName: String by project
val appVersionCode: String by project
val ensaiosStorePassword: String by project
val ensaiosKeyAlias: String by project
val ensaiosKeyPassword: String by project

android {
    namespace = "br.ensaios.servidor"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.ensaios.servidor"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode.toInt()
        versionName = appVersionName
    }

    signingConfigs {
        create("ensaios") {
            storeFile = rootProject.file("keystore/ensaios.jks")
            storePassword = ensaiosStorePassword
            keyAlias = ensaiosKeyAlias
            keyPassword = ensaiosKeyPassword
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ensaios")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("ensaios")
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
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/io.netty.versions.properties",
            )
        }
    }
}

dependencies {
    implementation(project(":shared"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val ktor = "2.3.12"
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-cio:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-server-compression:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")
}
