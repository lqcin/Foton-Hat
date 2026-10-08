package com.foton.dxfhelper

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

class CompletionStore(context: Context) {
    private val prefs = context.getSharedPreferences("dxf_completion", Context.MODE_PRIVATE)

    private fun key(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
        return "done_" + digest.take(12).joinToString("") { "%02x".format(it) }
    }

    fun load(uri: Uri): MutableSet<String> = prefs.getStringSet(key(uri), emptySet())?.toMutableSet() ?: mutableSetOf()

    fun save(uri: Uri, values: Set<String>) {
        prefs.edit().putStringSet(key(uri), values.toSet()).apply()
    }

    fun clear(uri: Uri) {
        prefs.edit().remove(key(uri)).apply()
    }
}
