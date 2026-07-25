package com.example.spikivegcs.server

import android.content.res.AssetManager
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class AssetWebServer(
    private val assetManager: AssetManager,
    private val assetRoot: String = "web",
    private val port: Int = DEFAULT_PORT,
) : Closeable {
    private val running = AtomicBoolean(false)
    private var closed = false
    private var serverSocket: ServerSocket? = null
    private var acceptExecutor: ExecutorService? = null
    private var clientExecutor: ExecutorService? = null

    val isRunning: Boolean
        get() = running.get()

    val url: String?
        get() = serverSocket?.localPort?.let { "http://127.0.0.1:$it/" }

    @Synchronized
    fun start(): String {
        check(!closed) { "Web server has already been closed" }
        url?.takeIf { running.get() }?.let { return it }

        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), port), SERVER_BACKLOG)
        }
        val acceptPool = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "spikive-web-accept").apply { isDaemon = true }
        }
        val clients = Executors.newFixedThreadPool(CLIENT_THREADS) { runnable ->
            Thread(runnable, "spikive-web-client").apply { isDaemon = true }
        }

        serverSocket = socket
        acceptExecutor = acceptPool
        clientExecutor = clients
        running.set(true)
        acceptPool.execute { acceptLoop(socket, clients) }
        return checkNotNull(url)
    }

    private fun acceptLoop(socket: ServerSocket, clients: ExecutorService) {
        while (running.get()) {
            try {
                val client = socket.accept()
                clients.execute { client.use(::handleClient) }
            } catch (error: SocketException) {
                if (running.get()) {
                    Log.e(TAG, "Loopback server socket failed", error)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Unable to accept loopback request", error)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.soTimeout = CLIENT_TIMEOUT_MS
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())

        try {
            val requestLine = readAsciiLine(input) ?: return
            val requestParts = requestLine.split(' ')
            if (requestParts.size < 2) {
                writeTextResponse(output, 400, "Bad Request", "Malformed request")
                return
            }

            val method = requestParts[0]
            val target = requestParts[1]
            consumeHeaders(input)

            if (method != "GET" && method != "HEAD") {
                writeTextResponse(
                    output = output,
                    statusCode = 405,
                    reason = "Method Not Allowed",
                    body = "Only GET and HEAD are supported",
                    extraHeaders = mapOf("Allow" to "GET, HEAD"),
                    includeBody = method != "HEAD",
                )
                return
            }

            val resolution = AssetRequestResolver.resolve(target)
            if (resolution == null) {
                writeTextResponse(output, 400, "Bad Request", "Invalid asset path", includeBody = method != "HEAD")
                return
            }

            val asset = openAsset(resolution)
            if (asset == null) {
                writeTextResponse(output, 404, "Not Found", "Asset not found", includeBody = method != "HEAD")
                return
            }

            asset.use { stream ->
                writeStreamingHeaders(
                    output = output,
                    statusCode = 200,
                    reason = "OK",
                    contentType = AssetRequestResolver.mimeType(resolution.assetPath),
                    extraHeaders = mapOf(
                        "Cache-Control" to cacheControl(resolution.assetPath),
                        "Cross-Origin-Opener-Policy" to "same-origin",
                        "Cross-Origin-Embedder-Policy" to "credentialless",
                        "Cross-Origin-Resource-Policy" to "same-origin",
                        "Content-Security-Policy" to "base-uri 'self'",
                        "Referrer-Policy" to "origin",
                        "X-Frame-Options" to "DENY",
                    ),
                )
                if (method != "HEAD") {
                    writeChunkedBody(output, stream)
                }
                output.flush()
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to serve loopback asset", error)
            runCatching {
                writeTextResponse(output, 500, "Internal Server Error", "Unable to serve asset")
            }
        }
    }

    private fun openAsset(resolution: AssetResolution) = try {
        assetManager.open("$assetRoot/${resolution.assetPath}", AssetManager.ACCESS_STREAMING)
    } catch (_: FileNotFoundException) {
        null
    } catch (_: IOException) {
        null
    }

    private fun consumeHeaders(input: BufferedInputStream) {
        while (true) {
            val line = readAsciiLine(input) ?: return
            if (line.isEmpty()) {
                return
            }
        }
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() <= MAX_HEADER_LINE_BYTES) {
            val value = input.read()
            if (value == -1) {
                return if (bytes.size() == 0) null else bytes.toString(StandardCharsets.US_ASCII.name())
            }
            if (value == '\n'.code) {
                return bytes.toString(StandardCharsets.US_ASCII.name()).trimEnd('\r')
            }
            bytes.write(value)
        }
        throw IllegalArgumentException("HTTP header line is too long")
    }

    private fun writeTextResponse(
        output: BufferedOutputStream,
        statusCode: Int,
        reason: String,
        body: String,
        extraHeaders: Map<String, String> = emptyMap(),
        includeBody: Boolean = true,
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        writeHeaders(output, statusCode, reason, "text/plain; charset=utf-8", bytes.size, extraHeaders)
        if (includeBody) {
            output.write(bytes)
        }
        output.flush()
    }

    private fun writeHeaders(
        output: BufferedOutputStream,
        statusCode: Int,
        reason: String,
        contentType: String,
        contentLength: Int,
        extraHeaders: Map<String, String>,
    ) {
        val headers = buildString {
            append("HTTP/1.1 $statusCode $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: $contentLength\r\n")
            append("Connection: close\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            extraHeaders.forEach { (name, value) -> append("$name: $value\r\n") }
            append("\r\n")
        }
        output.write(headers.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun writeStreamingHeaders(
        output: BufferedOutputStream,
        statusCode: Int,
        reason: String,
        contentType: String,
        extraHeaders: Map<String, String>,
    ) {
        val headers = buildString {
            append("HTTP/1.1 $statusCode $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Transfer-Encoding: chunked\r\n")
            append("Connection: close\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            extraHeaders.forEach { (name, value) -> append("$name: $value\r\n") }
            append("\r\n")
        }
        output.write(headers.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun writeChunkedBody(output: BufferedOutputStream, input: java.io.InputStream) {
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) {
                break
            }
            output.write(count.toString(16).toByteArray(StandardCharsets.US_ASCII))
            output.write(CRLF)
            output.write(buffer, 0, count)
            output.write(CRLF)
        }
        output.write(LAST_CHUNK)
    }

    private fun cacheControl(assetPath: String): String = if (CONTENT_HASH_PATTERN.containsMatchIn(assetPath)) {
        "public, max-age=31536000, immutable"
    } else {
        "no-cache"
    }

    @Synchronized
    override fun close() {
        closed = true
        if (!running.getAndSet(false)) {
            return
        }
        runCatching { serverSocket?.close() }
        acceptExecutor?.shutdownNow()
        clientExecutor?.shutdownNow()
        serverSocket = null
        acceptExecutor = null
        clientExecutor = null
    }

    private companion object {
        const val TAG = "AssetWebServer"
        const val LOOPBACK_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 8766
        const val SERVER_BACKLOG = 50
        const val CLIENT_THREADS = 8
        const val CLIENT_TIMEOUT_MS = 10_000
        const val MAX_HEADER_LINE_BYTES = 16_384
        const val STREAM_BUFFER_BYTES = 64 * 1024
        val CONTENT_HASH_PATTERN = Regex("\\.[0-9a-fA-F]{8,}\\.")
        val CRLF = "\r\n".toByteArray(StandardCharsets.US_ASCII)
        val LAST_CHUNK = "0\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
    }
}
