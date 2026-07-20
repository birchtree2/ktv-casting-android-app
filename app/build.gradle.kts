import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties()
val localFile = file("local.properties")
if (localFile.exists()) localProps.load(localFile.inputStream())
val repoOwner = project.findProperty("repo_owner") as String?
    ?: localProps.getProperty("repo_owner")
    ?: "KARAOKE-MASTER-ZJU"
val repoName = project.findProperty("repo_name") as String?
    ?: localProps.getProperty("repo_name")
    ?: "ktv-casting-android-app"

android {
    namespace = "zju.bangdream.ktv.casting"
    compileSdk = 36

    val appVersionName = (project.findProperty("app_version_name") as String?)
        ?: System.getenv("APP_CODE_VERSION")
        ?: "1.1.0"

    defaultConfig {
        applicationId = "zju.bangdream.ktv.casting"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GITHUB_REPO_OWNER", "\"$repoOwner\"")
        buildConfigField("String", "GITHUB_REPO_NAME", "\"$repoName\"")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    // 确保 Gradle 能找到 GitHub Actions 下载的 .so 文件
    sourceSets {
        getByName("main") {
            jniLibs.directories.add("src/main/jniLibs")
        }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.zxing.core)

    // 更新检查
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("packRelease") {
    group = "Pack apk"
    dependsOn("assembleRelease")

    doLast {
        val projectName = "KTV-Casting"
        val ver = android.defaultConfig.versionName ?: "1.0.0"
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        // 获取 build 目录
        val apkDir = File(layout.buildDirectory.get().asFile, "outputs/apk/release")
        val targetDir = File(rootDir, "apks")

        if (apkDir.exists()) {
            apkDir.listFiles { f -> f.extension == "apk" }?.forEach { apkFile ->
                val abi = when {
                    apkFile.name.contains("arm64-v8a") -> "arm64-v8a"
                    apkFile.name.contains("armeabi-v7a") -> "armeabi-v7a"
                    apkFile.name.contains("x86_64") -> "x86_64"
                    apkFile.name.contains("x86") -> "x86"
                    apkFile.name.contains("universal") -> "universal"
                    else -> "release"
                }
                copy {
                    from(apkFile)
                    into(targetDir)
                    rename { "$projectName-v$ver-$abi.apk" }
                }
            }
        }
    }
}