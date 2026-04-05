plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val androidCompileSdk = providers.gradleProperty("ANDROID_COMPILE_SDK").get().toInt()
val androidMinSdk = providers.gradleProperty("ANDROID_MIN_SDK").get().toInt()
val appApplicationId = providers.gradleProperty("APP_APPLICATION_ID").get()
val appVersionCode = providers.gradleProperty("APP_VERSION_CODE").get().toInt()
val appVersionName = providers.gradleProperty("APP_VERSION_NAME").get()
val appleCmsBaseUrl = providers.gradleProperty("APPLE_CMS_BASE_URL").get()

android {
    namespace = "top.jlen.vod.core.common"
    compileSdk = androidCompileSdk

    defaultConfig {
        minSdk = androidMinSdk
        buildConfigField("String", "APP_APPLICATION_ID", "\"$appApplicationId\"")
        buildConfigField("int", "APP_VERSION_CODE", appVersionCode.toString())
        buildConfigField("String", "APP_VERSION_NAME", "\"$appVersionName\"")
        buildConfigField("String", "APPLE_CMS_BASE_URL", "\"$appleCmsBaseUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}
