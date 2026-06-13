package com.tankxu.roborock.watch

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.tankxu.roborock.watch.databinding.ActivityMainBinding
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var miio: Miio
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loopJob: Job? = null

    @Volatile private var dir = 0          // 0=无 1=前进 2=后退 3=左转 4=右转

    private val SPEED = 0.29               // m/s
    private val TURN = 0.5                 // rad ≈ 28.6°

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        buildMiio()
        wireButtons()
        b.status.setOnClickListener { showSettings() }
    }

    private fun prefs() = getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private fun cfgIp() = prefs().getString("ip", Config.VAC_IP)!!
    private fun cfgToken() = prefs().getString("token", Config.VAC_TOKEN)!!
    private fun cfgDid() = prefs().getLong("did", Config.VAC_DID)
    private fun buildMiio() { miio = Miio(cfgIp(), cfgToken(), cfgDid()) }

    private fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address && (it.hostAddress?.startsWith("192.168") == true) }
            ?.hostAddress
    } catch (e: Exception) { null }

    private fun setStatus(text: String, colorRes: Int) = runOnUiThread {
        b.status.text = "● $text"
        b.status.setTextColor(getColor(colorRes))
    }

    private fun scale(v: android.view.View, s: Float) {
        v.animate().scaleX(s).scaleY(s).setDuration(80).start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireButtons() {
        val map = mapOf(b.btnUp to 1, b.btnDown to 2, b.btnLeft to 3, b.btnRight to 4)
        for ((btn, d) in map) {
            btn.setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { dir = d; v.isPressed = true; scale(v, 0.93f); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dir = 0; v.isPressed = false; scale(v, 1f); true }
                    else -> false
                }
            }
        }
        b.btnHome.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> scale(v, 0.93f)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scale(v, 1f)
            }
            false   // 不消费, 让 onClick 仍触发
        }
        b.btnHome.setOnClickListener {
            scope.launch {
                miio.send("app_rc_end", miio.arr(), localIp())
                val ok = miio.send("app_charge", miio.arr(), localIp())
                setStatus(if (ok) "在线" else "离线", if (ok) R.color.ok else R.color.bad)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startLoop()
    }

    override fun onPause() {
        super.onPause()
        dir = 0
        loopJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            val c = miio.connect(localIp())
            setStatus(if (c) "在线" else "离线", if (c) R.color.ok else R.color.bad)
            var rc = false; var seq = 0; var lastSend = 0L; var idleSince = 0L; var movingPrev = false; var lastConn = 0L
            while (isActive) {
                val d = dir
                val now = System.currentTimeMillis()
                val moving = d != 0
                if (moving) {
                    if (!rc) { miio.send("app_rc_start", miio.arr(), localIp()); rc = true; seq = 0 }
                    if (now - lastSend > 250) {
                        val v = when (d) { 1 -> SPEED; 2 -> -SPEED; else -> 0.0 }
                        val w = when (d) { 3 -> TURN; 4 -> -TURN; else -> 0.0 }
                        seq++
                        val p = JSONObject().put("omega", w).put("velocity", v)
                            .put("duration", 1000).put("seqnum", seq)
                        val ok = miio.send("app_rc_move", miio.arr(p), localIp())
                        setStatus(if (ok) "在线" else "离线", if (ok) R.color.ok else R.color.bad)
                        lastSend = now
                    }
                } else if (rc) {
                    if (movingPrev) {
                        seq++
                        val p = JSONObject().put("omega", 0.0).put("velocity", 0.0)
                            .put("duration", 500).put("seqnum", seq)
                        miio.send("app_rc_move", miio.arr(p), localIp())
                        idleSince = now
                        setStatus("在线", R.color.ok)
                    } else if (now - idleSince > 8000) {
                        miio.send("app_rc_end", miio.arr(), localIp()); rc = false
                    }
                }
                if (!moving && !miio.connected && now - lastConn > 3000) {   // 空闲时自动重连
                    lastConn = now
                    val ok = miio.connect(localIp())
                    setStatus(if (ok) "在线" else "离线", if (ok) R.color.ok else R.color.bad)
                }
                movingPrev = moving
                delay(60)
            }
        }
    }

    private fun showSettings() {
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val ip = EditText(this).apply { hint = "扫地机 IP"; setText(cfgIp()); inputType = InputType.TYPE_CLASS_TEXT }
        val tk = EditText(this).apply { hint = "token (32位hex)"; setText(cfgToken()); inputType = InputType.TYPE_CLASS_TEXT }
        val did = EditText(this).apply { hint = "DID"; setText(cfgDid().toString()); inputType = InputType.TYPE_CLASS_NUMBER }
        box.addView(ip); box.addView(tk); box.addView(did)
        AlertDialog.Builder(this)
            .setTitle("扫地机配置")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                prefs().edit()
                    .putString("ip", ip.text.toString().trim())
                    .putString("token", tk.text.toString().trim())
                    .putLong("did", did.text.toString().trim().toLongOrNull() ?: cfgDid())
                    .apply()
                buildMiio()
                startLoop()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
