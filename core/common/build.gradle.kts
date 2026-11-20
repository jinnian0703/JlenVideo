import java.net.URI

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

val androidCompileSdk = providers.gradleProperty("ANDROID_COMPILE_SDK").get().toInt()
val androidMinSdk = providers.gradleProperty("ANDROID_MIN_SDK").get().toInt()
val appApplicationId = providers.gradleProperty("APP_APPLICATION_ID").get()
val appVersionCode = providers.gradleProperty("APP_VERSION_CODE").get().toInt()
val appVersionName = providers.gradleProperty("APP_VERSION_NAME").get()
val appleCmsBaseUrl = URI(providers.gradleProperty("APPLE_CMS_BASE_URL").get().trim()).apply {
    require(scheme.equals("https", ignoreCase = true) && host != null && userInfo == null && query == null && fragment == null) {
        "APPLE_CMS_BASE_URL must be an HTTPS base URL without credentials, query or fragment"
    }
}

android {
    namespace = "top.jlen.vod.core.common"
    compileSdk = androidCompileSdk

    defaultConfig {
        minSdk = androidMinSdk
        buildConfigField("String", "APP_APPLICATION_ID", "\"$appApplicationId\"")
        buildConfigField("int", "APP_VERSION_CODE", appVersionCode.toString())
        buildConfigField("String", "APP_VERSION_NAME", "\"$appVersionName\"")
        buildConfigField("String", "APPLE_CMS_BASE_URL", "\"${appleCmsBaseUrl.toASCIIString().trimEnd('/')}/\"")
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
