package com.example.orderease

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

class OrderEaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Get the current system language
        val systemLocale = Locale.getDefault().language
        
        // Only set the app language if the user hasn't manually set one yet
        // Check if there are no app-specific locales set
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            val languageToSet = if (systemLocale == "zh") "zh" else "en"
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageToSet)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }
}
