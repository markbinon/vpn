package com.myvpn.app

import android.net.VpnService
import android.util.Base64
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Локальный SOCKS5-сервер на 127.0.0.1, который каждое CONNECT превращает в
 * HTTP CONNECT через вшитый прокси (с Basic-авторизацией).
 * UDP не поддерживается (HTTP-прокси его не умеет).
 */
class ProxyBridge(private val vpn: VpnService, private val port: Int) {

    private val pool: ExecutorService = Executors.newCachedThreadPool()
    @Volatile private var server: ServerSocket? = null

    private val authHeader: String = "Basic " + Base64.encodeToString(
        "${BuildConfig.PROXY_USER}:${BuildConfig.PROXY_PASS}".toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP
    )

    fun start() {
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 256)
        server = s
        pool.execute {
            while (!s.isClosed) {
                try {
                    val c = s.accept()
                    pool.execute { handle(c) }
                } catch (e: IOException) {
                    break
                }
            }
        }
    }

    fun stop() {
        try { server?.close() } catch (_: IOException) {}
        pool.shutdownNow()
    }

    private fun handle(client: Socket) {
        var upstream: Socket? = null
        try {
            client.tcpNoDelay = true
            val cin = client.getInputStream()
            val cout = client.getOutputStream()

            // Приветствие SOCKS5: отвечаем «без авторизации»
            if (cin.readByte() != 5) return
            val nMethods = cin.readByte()
            cin.skipFully(nMethods)
            cout.write(byteArrayOf(5, 0))
            cout.flush()

            // Запрос
            if (cin.readByte() != 5) return
            val cmd = cin.readByte()
            cin.readByte() // RSV
            val host = when (cin.readByte()) {
                1 -> InetAddress.getByAddress(cin.readBytes(4)).hostAddress
                3 -> String(cin.readBytes(cin.readByte()), Charsets.US_ASCII)
                4 -> "[" + InetAddress.getByAddress(cin.readBytes(16)).hostAddress + "]"
                else -> { reply(cout, 8); return }
            }
            val port = (cin.readByte() shl 8) or cin.readByte()

            if (cmd != 1) { reply(cout, 7); return } // только CONNECT

            val up = Socket()
            upstream = up
            vpn.protect(up) // чтобы трафик к прокси не зациклился в туннель
            up.tcpNoDelay = true
            up.connect(InetSocketAddress(BuildConfig.PROXY_HOST, BuildConfig.PROXY_PORT), 10_000)

            val uin = up.getInputStream()
            val uout = up.getOutputStream()
            val req = "CONNECT $host:$port HTTP/1.1\r\n" +
                "Host: $host:$port\r\n" +
                "Proxy-Authorization: $authHeader\r\n" +
                "Proxy-Connection: keep-alive\r\n\r\n"
            uout.write(req.toByteArray(Charsets.US_ASCII))
            uout.flush()

            val status = readHeaders(uin)
            if (status != 200) { reply(cout, if (status == 407) 2 else 5); return }

            reply(cout, 0)

            // Двусторонняя пересылка
            val back = pool.submit {
                copy(uin, cout)
                try { client.shutdownOutput() } catch (_: IOException) {}
            }
            copy(cin, uout)
            try { up.shutdownOutput() } catch (_: IOException) {}
            try { back.get() } catch (_: Exception) {}
        } catch (_: Exception) {
            // соединение оборвано — просто закрываем
        } finally {
            try { upstream?.close() } catch (_: IOException) {}
            try { client.close() } catch (_: IOException) {}
        }
    }

    /** Читает ответ прокси до пустой строки, возвращает HTTP-код (или -1). */
    private fun readHeaders(input: InputStream): Int {
        val sb = StringBuilder()
        while (sb.length < 8192) {
            val b = input.read()
            if (b < 0) return -1
            sb.append(b.toChar())
            if (sb.endsWith("\r\n\r\n")) break
        }
        val line = sb.lineSequence().firstOrNull() ?: return -1
        return line.split(" ").getOrNull(1)?.toIntOrNull() ?: -1
    }

    private fun reply(out: OutputStream, code: Int) {
        out.write(byteArrayOf(5, code.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
        out.flush()
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buf = ByteArray(16 * 1024)
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                output.write(buf, 0, n)
                output.flush()
            }
        } catch (_: IOException) {
        }
    }

    private fun InputStream.readByte(): Int {
        val b = read()
        if (b < 0) throw IOException("EOF")
        return b
    }

    private fun InputStream.readBytes(n: Int): ByteArray {
        val a = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = read(a, off, n - off)
            if (r < 0) throw IOException("EOF")
            off += r
        }
        return a
    }

    private fun InputStream.skipFully(n: Int) { readBytes(n) }
}
