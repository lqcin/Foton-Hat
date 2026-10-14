package com.foton.dxfhelper

import android.content.Context

/**
 * FOTON Klavye <-> FOTONMAP Activity arasındaki kalıcı köprü.
 *
 * Harita Activity'si task reuse / resume sırasında Intent extra kaybetse bile
 * seçim isteği ve bekleyen kesit SharedPreferences üzerinden korunur.
 */
object ImeSelectionStore {
    private const val PREF = "foton_ime_bridge"
    private const val KEY_PENDING = "pending_section"
    private const val KEY_REQUEST_ACTIVE = "selection_request_active"
    private const val KEY_LAST_SECTION = "last_selected_section"

    fun save(context: Context, sectionNo: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING, sectionNo)
            .putBoolean(KEY_REQUEST_ACTIVE, false)
            .commit()
    }

    fun peek(context: Context): String? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_PENDING, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING)
            .putBoolean(KEY_REQUEST_ACTIVE, false)
            .commit()
    }

    /**
     * Yeni bir harita seçimi başlarken eski bekleyen seçimi temizler.
     * Böylece kullanıcı yeni hat seçerken eski Axx-Ayy yanlışlıkla yazılamaz.
     */
    fun beginSelectionRequest(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING)
            .putBoolean(KEY_REQUEST_ACTIVE, true)
            .commit()
    }

    fun isSelectionRequestActive(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_REQUEST_ACTIVE, false)

    fun cancelSelectionRequest(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REQUEST_ACTIVE, false)
            .commit()
    }


    fun saveLastSection(context: Context, sectionNo: String) {
        if (sectionNo.isBlank()) return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SECTION, sectionNo)
            .commit()
    }

    fun getLastSection(context: Context): String? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SECTION, null)

    fun clearLastSection(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_SECTION)
            .commit()
    }
}
