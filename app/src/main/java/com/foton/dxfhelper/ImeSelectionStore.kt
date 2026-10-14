package com.foton.dxfhelper

import android.content.Context

object ImeSelectionStore {
    private const val PREF = "foton_ime_bridge"
    private const val KEY_PENDING = "pending_section"

    fun save(context: Context, sectionNo: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_PENDING, sectionNo).apply()
    }

    fun peek(context: Context): String? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_PENDING, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY_PENDING).apply()
    }
}
