package com.foton.dxfhelper

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * .fotonmap dosyasının saha durumunu kendi dosyasının içine yazar.
 *
 * Dosyaya yalnızca:
 *   "completed": ["1DA-0", "1DB-0", ...]
 * eklenir / güncellenir.
 *
 * Tarih, saat, "tamamlandı" metni vb. tekrar tekrar tutulmaz.
 */
object FotonMapFileStore {

    fun readBestEffort(context: Context, uri: Uri): String {
        val primary = readUri(context.contentResolver, uri)

        if (isValidFotonMap(primary)) {
            return primary
        }

        val backup = backupFile(context, uri)
        if (backup.exists()) {
            val backupRaw = backup.readText(StandardCharsets.UTF_8)
            if (isValidFotonMap(backupRaw)) {
                return backupRaw
            }
        }

        return primary
    }

    /**
     * Aynı SAF URI üzerine güncel JSON yazılır.
     * Yazmadan önce son sağlam dosya uygulamanın private backup alanına alınır.
     */
    fun writeCompleted(
        context: Context,
        uri: Uri,
        completedIds: Set<String>
    ) {
        val resolver = context.contentResolver
        val oldRaw = readUri(resolver, uri)

        require(isValidFotonMap(oldRaw)) {
            "Mevcut FOTONMAP geçerli değil; üzerine yazılmadı."
        }

        val root = JSONObject(oldRaw)

        val completedArray = JSONArray()
        completedIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .forEach { completedArray.put(it) }

        root.put("completed", completedArray)

        // Compact JSON: completed bilgisi eklense bile dosya gereksiz büyümez.
        val updated = root.toString()

        // Yazmadan önce kendi private alanımızda son sağlam kopyayı tut.
        backupFile(context, uri)
            .writeText(oldRaw, StandardCharsets.UTF_8)

        try {
            writeUri(resolver, uri, updated)

            // Yazdıktan sonra bir kez geri okuyup doğrula.
            val verify = readUri(resolver, uri)
            require(isValidFotonMap(verify)) {
                "FOTONMAP yazma sonrası doğrulanamadı."
            }

            val verifyRoot = JSONObject(verify)
            val verifyArray = verifyRoot.optJSONArray("completed")
            val verifiedIds = mutableSetOf<String>()

            if (verifyArray != null) {
                for (i in 0 until verifyArray.length()) {
                    val id = verifyArray.optString(i, "").trim()
                    if (id.isNotBlank()) verifiedIds += id
                }
            }

            require(verifiedIds == completedIds.filter { it.isNotBlank() }.toSet()) {
                "Tamamlanan hat listesi doğrulanamadı."
            }
        } catch (t: Throwable) {
            // En iyi gayretle kaynağı son sağlam haline geri döndür.
            runCatching {
                writeUri(resolver, uri, oldRaw)
            }
            throw t
        }
    }

    private fun readUri(
        resolver: ContentResolver,
        uri: Uri
    ): String =
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "FOTONMAP açılamadı." }
            input.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }

    private fun writeUri(
        resolver: ContentResolver,
        uri: Uri,
        text: String
    ) {
        val output =
            try {
                resolver.openOutputStream(uri, "wt")
            } catch (_: Throwable) {
                resolver.openOutputStream(uri)
            }

        output.use { stream ->
            requireNotNull(stream) { "FOTONMAP yazma için açılamadı." }
            stream.write(text.toByteArray(StandardCharsets.UTF_8))
            stream.flush()
        }
    }

    private fun isValidFotonMap(raw: String): Boolean =
        runCatching {
            val root = JSONObject(raw)
            root.optString("format")
                .equals("FOTONMAP", ignoreCase = true) &&
                root.optJSONArray("segments") != null
        }.getOrDefault(false)

    private fun backupFile(
        context: Context,
        uri: Uri
    ): File {
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(uri.toString().toByteArray(StandardCharsets.UTF_8))
                .take(12)
                .joinToString("") { "%02x".format(it) }

        return File(
            context.filesDir,
            "fotonmap_backup_$digest.json"
        )
    }
}
