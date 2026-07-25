package com.example.spikivegcs.server

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal data class AssetResolution(
    val assetPath: String,
    val isSpaFallback: Boolean,
)

internal object AssetRequestResolver {
    private const val INDEX_PATH = "index.html"

    fun resolve(rawTarget: String): AssetResolution? {
        val path = rawTarget.substringBefore('?').substringBefore('#')
        val decodedPath = runCatching {
            URLDecoder.decode(path, StandardCharsets.UTF_8.name())
        }.getOrNull() ?: return null

        val normalizedPath = decodedPath.trimStart('/')
        if (normalizedPath.isBlank()) {
            return AssetResolution(INDEX_PATH, isSpaFallback = false)
        }

        val segments = normalizedPath.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." || it.contains('\\') }) {
            return null
        }

        val assetPath = segments.joinToString("/")
        val lastSegment = segments.last()
        return if (lastSegment.contains('.')) {
            AssetResolution(assetPath, isSpaFallback = false)
        } else {
            AssetResolution(INDEX_PATH, isSpaFallback = true)
        }
    }

    fun mimeType(assetPath: String): String = when (assetPath.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html; charset=utf-8"
        "js", "mjs" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "json", "map" -> "application/json; charset=utf-8"
        "wasm" -> "application/wasm"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "txt" -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
    }
}
