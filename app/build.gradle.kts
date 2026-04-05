plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val androidCompileSdk = providers.gradleProperty("ANDROID_COMPILE_SDK").get().toInt()
val androidMinSdk = providers.gradleProperty("ANDROID_MIN_SDK").get().toInt()
val androidTargetSdk = providers.gradleProperty("ANDROID_TARGET_SDK").get().toInt()
val appApplicationId = providers.gradleProperty("APP_APPLICATION_ID").get()
val appVersionCode = providers.gradleProperty("APP_VERSION_CODE").get().toInt()
val appVersionName = providers.gradleProperty("APP_VERSION_NAME").get()
val appleCmsBaseUrl = providers.gradleProperty("APPLE_CMS_BASE_URL").get()

android {
    namespace = "top.jlen.vod"
    compileSdk = androidCompileSdk

    defaultConfig {
        applicationId = appApplicationId
        minSdk = androidMinSdk
        targetSdk = androidTargetSdk
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "APPLE_CMS_BASE_URL", "\"$appleCmsBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

afterEvaluate {
    android.applicationVariants.all {
        val variant = this
        val assembleTaskName = "assemble" + name.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }

        tasks.named(assembleTaskName).configure {
            doLast {
                val outputDir = layout.buildDirectory.dir("outputs/apk/${variant.name}").get().asFile
                val sourceApk = outputDir.listFiles()
                    ?.filter { file ->
                        file.isFile &&
                            file.extension.equals("apk", ignoreCase = true) &&
                            !file.name.startsWith("JlenVideo-")
                    }
                    ?.maxByOrNull { it.lastModified() }
                    ?: return@doLast

                val resolvedVersionName = android.defaultConfig.versionName ?: "dev"
                val targetName = "JlenVideo-$resolvedVersionName-${variant.name}.apk"
                val targetApk = outputDir.resolve(targetName)
                if (targetApk.exists()) {
                    targetApk.delete()
                }
                copy {
                    from(sourceApk)
                    into(outputDir)
                    rename { targetName }
                }
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":feature:shell"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
