plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.youtubecompress"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.youtubecompress"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // yt-dlp ve ffmpeg ikili dosyaları için işlemci mimarisi filtreleri
        ndk {
            abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    // Yerel kütüphane paketleme ayarı (Hatayı çözen kısım)
    packaging {
        jniLibs.useLegacyPackaging = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Arka plan işlemleri ve Wi-Fi kuyruğu için
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Youtubedl-android kütüphanesi ve ffmpeg desteği
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}