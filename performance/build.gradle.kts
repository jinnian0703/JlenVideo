plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "top.jlen.vod.performance.test"
    compileSdk = providers.gradleProperty("ANDROID_COMPILE_SDK").get().toInt()
    defaultConfig {
        minSdk = 28
        targetSdk = providers.gradleProperty("ANDROID_TARGET_SDK").get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        create("profile") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType != "debug" && it.buildType != "release" }
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    // 与本项目 compileSdk 34 / Kotlin 1.9 相容；不升级生产依赖。
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
}
