package com.myvpn.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var button: Button
    private val reqVpn = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#101418"))
            setPadding((24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt())
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val size = (180 * dp).toInt()
        button = Button(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                topMargin = (48 * dp).toInt()
                bottomMargin = (32 * dp).toInt()
            }
            setOnClickListener { onToggle() }
        }

        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
        }

        root.addView(title)
        root.addView(button)
        root.addView(status)
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
    }

    override fun onStart() {
        super.onStart()
        VpnState.listener = { runOnUiThread { render() } }
        render()
    }

    override fun onStop() {
        VpnState.listener = null
        super.onStop()
    }

    private fun onToggle() {
        if (VpnState.running) {
            startService(Intent(this, MyVpnService::class.java).setAction(MyVpnService.ACTION_STOP))
            return
        }
        val prepare = VpnService.prepare(this)
        if (prepare != null) startActivityForResult(prepare, reqVpn) else startVpn()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqVpn && resultCode == RESULT_OK) startVpn()
    }

    private fun startVpn() {
        val i = Intent(this, MyVpnService::class.java).setAction(MyVpnService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        status.text = "Подключение…"
    }

    private fun render() {
        val on = VpnState.running
        button.text = if (on) "Отключить" else "Подключиться"
        (button.background as GradientDrawable)
            .setColor(Color.parseColor(if (on) "#2E7D32" else "#1E88E5"))
        status.text = when {
            on -> "Подключено"
            VpnState.error != null -> "Ошибка: ${VpnState.error}"
            else -> "Не подключено"
        }
    }
}
