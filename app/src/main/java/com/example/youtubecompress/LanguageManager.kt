package com.example.youtubecompress

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageManager {
    // Dil kodunu alır ("tr" veya "en") ve uygulamanın dilini anında değiştirir.
    fun changeLanguage(languageCode: String) {
        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}