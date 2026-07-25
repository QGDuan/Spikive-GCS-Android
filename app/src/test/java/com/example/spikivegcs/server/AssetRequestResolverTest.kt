package com.example.spikivegcs.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetRequestResolverTest {
    @Test
    fun rootResolvesToIndex() {
        val resolution = AssetRequestResolver.resolve("/")

        assertEquals("index.html", resolution?.assetPath)
        assertFalse(checkNotNull(resolution).isSpaFallback)
    }

    @Test
    fun clientRouteFallsBackToIndex() {
        val resolution = AssetRequestResolver.resolve("/workspace/robot-1?mode=flight")

        assertEquals("index.html", resolution?.assetPath)
        assertTrue(checkNotNull(resolution).isSpaFallback)
    }

    @Test
    fun concreteAssetDoesNotFallback() {
        val resolution = AssetRequestResolver.resolve("/main.1234.js")

        assertEquals("main.1234.js", resolution?.assetPath)
        assertFalse(checkNotNull(resolution).isSpaFallback)
    }

    @Test
    fun traversalIsRejected() {
        assertNull(AssetRequestResolver.resolve("/../index.html"))
        assertNull(AssetRequestResolver.resolve("/%2e%2e/index.html"))
        assertNull(AssetRequestResolver.resolve("/folder\\index.html"))
    }

    @Test
    fun mimeTypesCoverWebWorkersAndWasm() {
        assertEquals("text/javascript; charset=utf-8", AssetRequestResolver.mimeType("worker.js"))
        assertEquals("application/wasm", AssetRequestResolver.mimeType("decoder.wasm"))
        assertEquals("font/woff2", AssetRequestResolver.mimeType("font.woff2"))
    }
}
