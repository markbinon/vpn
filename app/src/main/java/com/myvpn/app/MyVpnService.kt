package com.myvpn.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import hev.htproxy.TProxyService
import java.io.File

class MyVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.myvpn.app.START"
        const val ACTION_STOP = "com.myvpn.app.STOP"
        const val SOCKS_PORT = 10808
        private const val CHANNEL = "vpn"
        private const val NOTIF_ID = 1
    }

    private var tun: ParcelFileDescriptor? = null
    private var bridge: ProxyBridge? = null
    private var tunnelStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cleanup()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        showNotification()
        if (tun == null) startVpn()
        return START_NOT_STICKY
    }

    private fun startVpn() {
        VpnState.error = null
        try {
            if (BuildConfig.PROXY_HOST.isEmpty() || BuildConfig.PROXY_PORT == 0) {
                throw IllegalStateException("Прокси не задан при сборке")
            }

            val b = ProxyBridge(this, SOCKS_PORT)
            b.start()
            bridge = b

            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress("198.18.0.1", 32)
                .addAddress("fc00::1", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("198.18.0.2")
            // само приложение (мост до прокси) в туннель не пускаем
            builder.addDisallowedApplication(packageName)

            val pfd = builder.establish() ?: throw IllegalStateException("Нет разрешения на VPN")
            tun = pfd

            val cfg = File(filesDir, "tproxy.yml")
            cfg.writeText(
                """
                misc:
                  task-stack-size: 81920
                  log-level: warn
                tunnel:
                  mtu: 1500
                  ipv4: 198.18.0.1
                  ipv6: 'fc00::1'
                socks5:
                  port: $SOCKS_PORT
                  address: 127.0.0.1
                mapdns:
                  address: 198.18.0.2
                  port: 53
                  network: 100.64.0.0
                  netmask: 255.192.0.0
                  cache-size: 10000
                """.trimIndent() + "\n"
            )
            if (!TProxyService.TProxyStartService(cfg.absolutePath, pfd.fd)) {
                throw IllegalStateException("Не удалось запустить туннель")
            }
            tunnelStarted = true
            VpnState.set(true)
        } catch (e: Throwable) {
            VpnState.error = e.toString()
            cleanup()
            stopForeground(true)
            stopSelf()
        }
    }

    private fun cleanup() {
        if (tunnelStarted) {
            try { TProxyService.TProxyStopService() } catch (_: Throwable) {}
            tunnelStarted = false
        }
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        bridge?.stop()
        bridge = null
        VpnState.set(false)
    }

    override fun onRevoke() {
        cleanup()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun showNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW)
                )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val nb = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
        else Notification.Builder(this)
        val n = nb.setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("VPN включён")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, n)
    }
}
