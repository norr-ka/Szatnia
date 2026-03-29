package com.example.szatnia.data

import android.content.Context

class SyncPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("szatnia_sync", Context.MODE_PRIVATE)

    fun getWebAppUrl(): String? {
        return preferences.getString(KEY_WEB_APP_URL, null)?.trim()?.ifBlank { null }
    }

    fun saveWebAppUrl(url: String) {
        preferences.edit().putString(KEY_WEB_APP_URL, url.trim()).apply()
    }

    companion object {
        private const val KEY_WEB_APP_URL = "web_app_url"
    }
}
