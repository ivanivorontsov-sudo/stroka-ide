package org.ivanivorontsov.stroka

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.SocketException
import kotlin.concurrent.thread

/** Минимальный HTTP/1.1-сервер для тестов (com.sun.net.httpserver недоступен в Android unit-тестах). */
class MockHttp(private val handler: (Request) -> Pair<Int, String>) : AutoCloseable {
    class Request(val method: String, val path: String, val headers: Map<String, String>, val body: String) {
        fun header(name: String): String? = headers[name.lowercase()]
    }

    private val server = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
    val base: String get() = "http://127.0.0.1:${server.localPort}"

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = try { server.accept() } catch (_: SocketException) { break }
                thread(isDaemon = true) {
                    s.use { sock ->
                        val input = sock.getInputStream()
                        val head = ByteArrayOutputStream()
                        var m = 0
                        while (m < 4) {
                            val b = input.read(); if (b < 0) return@use
                            head.write(b)
                            m = if ((m == 0 || m == 2) && b == '\r'.code) m + 1 else if ((m == 1 || m == 3) && b == '\n'.code) m + 1 else 0
                        }
                        val lines = head.toString(Charsets.ISO_8859_1.name()).split("\r\n").filter { it.isNotEmpty() }
                        val (method, path) = lines[0].split(" ").let { it[0] to it[1].substringBefore('?') }
                        val headers = lines.drop(1).associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
                        val len = headers["content-length"]?.toIntOrNull() ?: 0
                        val body = ByteArray(len); var off = 0
                        while (off < len) { val n = input.read(body, off, len - off); if (n < 0) break; off += n }
                        val (code, resp) = handler(Request(method, path, headers, String(body, Charsets.UTF_8)))
                        val bytes = resp.toByteArray(Charsets.UTF_8)
                        val out = sock.getOutputStream()
                        out.write(("HTTP/1.1 $code X\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1))
                        out.write(bytes); out.flush()
                    }
                }
            }
        }
    }

    override fun close() = server.close()
}
