package com.foton.dxfhelper

import android.content.Context

/**
 * FOTON Klavye <-> FOTONMAP Activity arasindaki kalici kopru.
 *
 * Intent extra Android tarafinda Activity reuse / task restore sirasinda
 * kaybolabildigi icin "selection request" bilgisi de SharedPreferences'ta
 * tutulur.
 */
object ImeSelectionStore {
    private const val PREF = "foton_ime_bridge"
    private const val KEY_PENDING = "pending_section"
    private const val KEY_REQUEST_ACTIVE = "selection_request_active"

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
            .edit().remove(KEY_PENDING).commit()
    }

    fun beginSelectionRequest(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
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
}
