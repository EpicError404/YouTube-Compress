package com.example.youtubecompress

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // FFmpeg önce, ardından YoutubeDL motoru başlatılıyor (FFmpeg konumu için sıra önemlidir)
            FFmpeg.init(this)
            YoutubeDL.init(this)
            Log.d("KOTADOSTU_HATA", "FFmpeg ve Youtubedl başarıyla başlatıldı.")
        } catch (e: Throwable) {
            // Oluşan tüm kritik sistem ve kütüphane hatalarını yakalıyoruz
            Log.e("KOTADOSTU_HATA", "Uygulama başlatılırken kritik hata oluştu: ", e)
        }
    }
}
