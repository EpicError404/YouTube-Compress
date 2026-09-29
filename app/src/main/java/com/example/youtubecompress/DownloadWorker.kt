package com.example.youtubecompress

import android.content.Context
import android.media.RingtoneManager
import android.os.Environment
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

class DownloadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val videoUrl = inputData.getString("video_url") ?: return Result.failure()
        val quality = inputData.getString("quality") ?: "720"

        val tempDir = File(applicationContext.cacheDir, "yt_downloads").apply {
            mkdirs()
            // Önceki indirmelerden kalan geçici dosyaları temizle
            listFiles()?.forEach { it.delete() }
        }

        return try {
            // Dosyanın kaydedileceği klasör: Downloads/CompressTube
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, "CompressTube")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            // Gerekirse yt-dlp ikili dosyasını güncelle
            try {
                YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
            } catch (e: Exception) {
                Log.w("KOTADOSTU_HATA", "yt-dlp güncellenemedi, mevcut sürüm kullanılacak: ${e.message}")
            }

            // yt-dlp indirme isteği (request) oluşturuluyor
            val request = YoutubeDLRequest(videoUrl).apply {
                addOption("--no-update")
                addOption("--no-mtime")
                addOption("--restrict-filenames")
                addOption("--no-playlist")
                addOption("--prefer-ffmpeg")
                if (quality == "audio") {
                    // Sadece Ses (MP3) İndirme Modu
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                } else {
                    // Seçilen Çözünürlükte Video + Ses Birleştirme Modu
                    addOption("-f", "bestvideo[height<=$quality][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=$quality]+bestaudio/best[height<=$quality]")
                    addOption("--merge-output-format", "mp4/mkv")
                }
                // Uygulamanın özel önbellek klasörüne güvenli indirme
                addOption("-o", "${tempDir.absolutePath}/%(title)s.%(ext)s")
            }

            // İndirme işlemini başlat ve ilerlemeyi logla / WorkManager'a bildir
            YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, _ ->
                val progressInt = progress.toInt()
                Log.d("DOWNLOAD_PROGRESS", "$progressInt% - ETA: $etaInSeconds s")
                setProgressAsync(
                    Data.Builder()
                        .putInt("progress", progressInt)
                        .putLong("eta", etaInSeconds)
                        .build(),
                )
            }

            var lastDownloadedFilePath = ""
            // Tamamlanan dosyaları nihai konuma (Downloads/CompressTube) kopyala
            tempDir.listFiles()?.forEach { downloadedFile ->
                if (downloadedFile.isFile) {
                    val destFile = File(targetDir, downloadedFile.name)
                    downloadedFile.copyTo(destFile, overwrite = true)
                    downloadedFile.delete()
                    lastDownloadedFilePath = destFile.absolutePath
                }
            }

            // İndirme tamamlandığında telefonun varsayılan bildirim zil sesini çal
            try {
                val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
                ringtone?.play()
            } catch (e: Exception) {
                Log.w("KOTADOSTU_HATA", "Bildirim sesi çalınamadı: ${e.message}")
            }

            val outputData = Data.Builder()
                .putString("file_path", lastDownloadedFilePath)
                .build()

            Result.success(outputData)
        } catch (e: Exception) {
            Log.e("KOTADOSTU_HATA", "İndirme başarısız oldu: ", e)
            Result.failure()
        } finally {
            // Ön bellek klasörünü temizle
            tempDir.listFiles()?.forEach { it.delete() }
        }
    }
}