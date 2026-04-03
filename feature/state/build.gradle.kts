plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val androidCompileSdk = providers.gradleProperty("ANDROID_COMPILE_SDK").get().toInt()
val androidMinSdk = providers.gradleProperty("ANDROID_MIN_SDK").get().toInt()

android {
    namespace = "top.jlen.vod.feature.state"
    compileSdk = androidCompileSdk

    defaultConfig {
        minSdk = androidMinSdk
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:design"))
    implementation(project(":feature:common"))
    implementation(project(":feature:player"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("androidx.compose.runtime:runtime:1.6.8")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
