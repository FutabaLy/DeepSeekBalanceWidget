import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.deepseek.balancewidget"
    compileSdk = 35

    // ---------------------------------------------------------------- 稳定签名
    // CI 通过环境变量 ORG_GRADLE_PROJECT_DSBW_* 注入（见 .github/workflows/build-apk.yml），
    // 本地没配就自动退回默认 debug 签名，Android Studio 直接打开也能跑。
    //
    // 为什么必须有它：GitHub runner 每次都重新生成 ~/.android/debug.keystore，
    // 用默认 debug 签名会导致每个新版本的签名都不一样 —— 新 APK 无法覆盖安装，
    // 只能卸载重装（API Key 等设置全部丢失）。
    val signStoreFile = project.providers.gradleProperty("DSBW_STORE_FILE").orNull
    val signStorePassword = project.providers.gradleProperty("DSBW_STORE_PASSWORD").orNull
    val signKeyAlias = project.providers.gradleProperty("DSBW_KEY_ALIAS").orNull
    val signKeyPassword = project.providers.gradleProperty("DSBW_KEY_PASSWORD").orNull
    val useStableSigning = signStoreFile != null && file(signStoreFile).exists() &&
        !signStorePassword.isNullOrBlank() && !signKeyAlias.isNullOrBlank()

    signingConfigs {
        if (useStableSigning) {
            create("stable") {
                storeFile = file(signStoreFile!!)
                storePassword = signStorePassword
                keyAlias = signKeyAlias
                // PKCS12 的 key 口令与库口令相同；单独给了就用单独的
                keyPassword = signKeyPassword ?: signStorePassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.deepseek.balancewidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            // 本地/CI 只要配了稳定签名，debug 包也用同一把钥匙，保证能覆盖安装
            if (useStableSigning) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        release {
            signingConfig = if (useStableSigning) {
                signingConfigs.getByName("stable")
            } else {
                // 没配签名时退回 debug 签名，至少保证能装（但换一次构建就换一把钥匙）
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // JVM 单测里跑真实 org.json（Android 框架里的实现只是 stub）
    testImplementation(libs.json)
}
