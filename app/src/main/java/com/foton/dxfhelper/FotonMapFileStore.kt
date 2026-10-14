package com.foton.dxfhelper

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object FotonMapFileStore {

    fun readBestEffort(
        context: Context,
        uri: Uri
    ): String {
        val primary =
            readUri(
                context.contentResolver,
                uri
            )

        if (isValidFotonMap(primary)) {
            return primary
        }

        val backup =
            backupFile(
                context,
                uri
            )

        if (backup.exists()) {
            val backupRaw =
                backup.readText(
                    StandardCharsets.UTF_8
                )

            if (isValidFotonMap(backupRaw)) {
                return backupRaw
            }
        }

        return primary
    }

    fun writeCompleted(
        context: Context,
        uri: Uri,
        completedIds: Set<String>
    ) {
        val resolver =
            context.contentResolver

        val oldRaw =
            readUri(
                resolver,
                uri
            )

        require(
            isValidFotonMap(oldRaw)
        ) {
            "Mevcut FOTONMAP geçerli değil; üzerine yazılmadı."
        }

        val root =
            JSONObject(oldRaw)

        val completedArray =
            JSONArray()

        completedIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .forEach {
                completedArray.put(it)
            }

        // Bilinmeyen / gelecekte eklenen tüm alanlar korunur.
        root.put(
            "completed",
            completedArray
        )

        val updated =
            root.toString()

        backupFile(
            context,
            uri
        ).writeText(
            oldRaw,
            StandardCharsets.UTF_8
        )

        try {
            writeUri(
                resolver,
                uri,
                updated
            )

            val verify =
                readUri(
                    resolver,
                    uri
                )

            require(
                isValidFotonMap(
                    verify
                )
            ) {
                "FOTONMAP yazma sonrası doğrulanamadı."
            }

            val verifyRoot =
                JSONObject(
                    verify
                )

            val verifyArray =
                verifyRoot
                    .optJSONArray(
                        "completed"
                    )

            val verifiedIds =
                mutableSetOf<String>()

            if (verifyArray != null) {
                for (
                    i in
                    0 until verifyArray.length()
                ) {
                    val id =
                        verifyArray
                            .optString(
                                i,
                                ""
                            )
                            .trim()

                    if (
                        id.isNotBlank()
                    ) {
                        verifiedIds += id
                    }
                }
            }

            require(
                verifiedIds ==
                    completedIds
                        .filter {
                            it.isNotBlank()
                        }
                        .toSet()
            ) {
                "Tamamlanan hat listesi doğrulanamadı."
            }
        } catch (t: Throwable) {
            runCatching {
                writeUri(
                    resolver,
                    uri,
                    oldRaw
                )
            }

            throw t
        }
    }

    private fun readUri(
        resolver: ContentResolver,
        uri: Uri
    ): String {
        val bytes =
            resolver
                .openInputStream(uri)
                .use { input ->
                    requireNotNull(input) {
                        "FOTONMAP açılamadı."
                    }

                    input.readBytes()
                }

        return decodeCompat(bytes)
    }

    /**
     * v1.0 exporter MBCS/Windows-1254 üretebiliyordu.
     * Önce strict UTF-8, olmazsa Windows-1254 denenir.
     */
    private fun decodeCompat(
        bytes: ByteArray
    ): String {
        val utf8 =
            runCatching {
                val decoder =
                    StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(
                            CodingErrorAction.REPORT
                        )
                        .onUnmappableCharacter(
                            CodingErrorAction.REPORT
                        )

                decoder
                    .decode(
                        ByteBuffer.wrap(bytes)
                    )
                    .toString()
            }
            .getOrNull()

        if (utf8 != null) {
            return utf8
        }

        return runCatching {
            String(
                bytes,
                charset("windows-1254")
            )
        }.getOrElse {
            String(
                bytes,
                StandardCharsets.ISO_8859_1
            )
        }
    }

    /**
     * SAF sağlayıcısı destekliyorsa rwt + truncate kullanılır.
     * Böylece yeni JSON eski dosyadan kısa olduğunda sonda artık byte kalmaz.
     */
    private fun writeUri(
        resolver: ContentResolver,
        uri: Uri,
        text: String
    ) {
        val bytes =
            text.toByteArray(
                StandardCharsets.UTF_8
            )

        val pfd =
            runCatching {
                resolver
                    .openFileDescriptor(
                        uri,
                        "rwt"
                    )
            }.getOrNull()

        if (pfd != null) {
            pfd.use {
                FileOutputStream(
                    it.fileDescriptor
                ).use { output ->
                    output.channel.truncate(0)
                    output.channel.position(0)
                    output.write(bytes)
                    output.flush()
                }
            }

            return
        }

        val output =
            try {
                resolver
                    .openOutputStream(
                        uri,
                        "wt"
                    )
            } catch (_: Throwable) {
                resolver
                    .openOutputStream(
                        uri
                    )
            }

        output.use { stream ->
            requireNotNull(stream) {
                "FOTONMAP yazma için açılamadı."
            }

            stream.write(bytes)
            stream.flush()
        }
    }

    private fun isValidFotonMap(
        raw: String
    ): Boolean =
        runCatching {
            val root =
                JSONObject(raw)

            root
                .optString("format")
                .equals(
                    "FOTONMAP",
                    ignoreCase = true
                ) &&
                (
                    root.optJSONArray("segments") != null ||
                    root.optJSONArray("lines") != null
                )
        }.getOrDefault(false)

    private fun backupFile(
        context: Context,
        uri: Uri
    ): File {
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(
                    uri.toString()
                        .toByteArray(
                            StandardCharsets.UTF_8
                        )
                )
                .take(12)
                .joinToString("") {
                    "%02x".format(it)
                }

        return File(
            context.filesDir,
            "fotonmap_backup_$digest.json"
        )
    }
}
