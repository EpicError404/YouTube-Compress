package com.example.youtubecompress

import android.app.DownloadManager
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI Bileşenleri
        val etVideoUrl = findViewById<EditText>(R.id.etVideoUrl)
        val switchWifi = findViewById<Switch>(R.id.switchWifi)
        val rgQuality = findViewById<RadioGroup>(R.id.rgQuality)
        val btnDownloadVideo = findViewById<Button>(R.id.btnDownloadVideo)
        val btnDownloadAudio = findViewById<Button>(R.id.btnDownloadAudio)
        val btnOpenFolder = findViewById<Button>(R.id.btnOpenFolder)
        val btnLanguage = findViewById<Button>(R.id.btnLanguage)

        // Dil Değiştirme Butonu
        btnLanguage.setOnClickListener {
            val currentLocales = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            val isTurkish = (currentLocales.startsWith("tr")) ||
                (currentLocales.isEmpty() && resources.configuration.locales[0].language == "tr")
            val nextLanguage = if (isTurkish) "en" else "tr"
            LanguageManager.changeLanguage(nextLanguage)
        }

        // İndirilen Dosyalarım / Klasör Butonu
        btnOpenFolder.setOnClickListener {
            openDownloadsFolder()
        }

        // Video + Ses İndirme Butonu
        btnDownloadVideo.setOnClickListener {
            val url = etVideoUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_enter_url), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Seçilen Kaliteyi Belirleme
            val quality = when (rgQuality.checkedRadioButtonId) {
                R.id.rb144 -> "144"
                R.id.rb240 -> "240"
                R.id.rb360 -> "360"
                R.id.rb480 -> "480"
                else -> "720"
            }

            startDownloadProcess(url, quality, switchWifi.isChecked)
        }

        // Sadece Ses İndirme Butonu
        btnDownloadAudio.setOnClickListener {
            val url = etVideoUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_enter_url), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startDownloadProcess(url, "audio", switchWifi.isChecked)
        }
    }

    private fun startDownloadProcess(url: String, quality: String, wifiOnly: Boolean) {
        val constraintsBuilder = Constraints.Builder()
        if (wifiOnly) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
        } else {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }

        val inputData = Data.Builder()
            .putString("video_url", url)
            .putString("quality", quality)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraintsBuilder.build())
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(this).enqueue(downloadRequest)

        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvProgress = findViewById<TextView>(R.id.tvProgress)
        val btnOpenFile = findViewById<Button>(R.id.btnOpenFile)
        val btnLanguage = findViewById<Button>(R.id.btnLanguage)

        // Aktif indirme başladığında "Dili Değiştir" butonunu gizle
        btnLanguage.visibility = View.GONE
        btnOpenFile.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        progressBar.progress = 0
        tvProgress.visibility = View.VISIBLE
        tvProgress.text = getString(R.string.download_starting)

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(downloadRequest.id)
            .observe(this) { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            btnLanguage.visibility = View.GONE
                            val progress = workInfo.progress.getInt("progress", -1)
                            val eta = workInfo.progress.getLong("eta", -1)
                            if (progress >= 0) {
                                progressBar.isIndeterminate = false
                                progressBar.progress = progress
                                if (eta > 0) {
                                    tvProgress.text = "%$progress (ETA: ${eta}s)"
                                } else {
                                    tvProgress.text = "%$progress"
                                }
                            } else {
                                progressBar.isIndeterminate = true
                                tvProgress.text = getString(R.string.download_preparing)
                            }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            btnLanguage.visibility = View.VISIBLE
                            val downloadedPath = workInfo.outputData.getString("file_path")
                            progressBar.isIndeterminate = false
                            progressBar.progress = 100
                            tvProgress.text = "${getString(R.string.download_completed)}\n${getString(R.string.download_location)}"
                            Toast.makeText(this, getString(R.string.download_success), Toast.LENGTH_SHORT).show()

                            if (!downloadedPath.isNullOrEmpty()) {
                                btnOpenFile.visibility = View.VISIBLE
                                btnOpenFile.setOnClickListener {
                                    openDownloadedFile(downloadedPath)
                                }
                            }
                        }
                        WorkInfo.State.FAILED -> {
                            btnLanguage.visibility = View.VISIBLE
                            progressBar.visibility = View.GONE
                            btnOpenFile.visibility = View.GONE
                            tvProgress.text = getString(R.string.download_failed)
                            Toast.makeText(this, getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
                        }
                        WorkInfo.State.CANCELLED -> {
                            btnLanguage.visibility = View.VISIBLE
                            progressBar.visibility = View.GONE
                            btnOpenFile.visibility = View.GONE
                            tvProgress.text = getString(R.string.download_cancelled)
                        }
                        else -> {}
                    }
                }
            }

        Toast.makeText(this, getString(R.string.download_queued), Toast.LENGTH_SHORT).show()
    }

    private fun openDownloadedFile(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                file,
            )
            val mimeType = contentResolver.getType(uri) ?: when (file.extension.lowercase()) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.btn_open_file)))
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDownloadsFolder() {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadsDir, "CompressTube")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val opened = try {
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                targetDir,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.btn_open_folder)))
            true
        } catch (_: Exception) {
            false
        }

        if (!opened) {
            try {
                val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "", Toast.LENGTH_SHORT).show()
            }
        }
    }
}