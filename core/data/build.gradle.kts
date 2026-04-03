plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val androidCompileSdk = providers.gradleProperty("ANDROID_COMPILE_SDK").get().toInt()
val androidMinSdk = providers.gradleProperty("ANDROID_MIN_SDK").get().toInt()

android {
    namespace = "top.jlen.vod.core.data"
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
    api(project(":core:model"))
    implementation(project(":core:common"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
