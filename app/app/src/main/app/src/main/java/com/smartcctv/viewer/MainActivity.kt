package com.smartcctv.viewer

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.graphics.Color
import android.text.InputType
import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class MainActivity : Activity() {
    private data class Config(
        var host: String = "192.168.1.240",
        var port: String = "554",
        var username: String = "",
        var password: String = "",
        var path: String = "/cam/realmonitor?channel=%d&subtype=%d"
    )

    private data class Tile(
        val frame: FrameLayout,
        val video: VLCVideoLayout,
        var vlc: LibVLC? = null,
        var player: MediaPlayer? = null,
        var channel: Int = 1
    )

    private val prefs by lazy { getSharedPreferences("cctv_private_settings", MODE_PRIVATE) }
    private var config = Config()
    private var root: LinearLayout? = null
    private var grid: GridLayout? = null
    private var status: TextView? = null
    private val tiles = mutableListOf<Tile>()
    private var layoutCount = 4
    private var selected = 0
    private var fullScreen = false
    private var savedFocus = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        loadConfig()
        showHome()
        if (config.username.isNotBlank()) connectLayout()
    }

    private fun loadConfig() {
        config = Config(
            prefs.getString("host", "192.168.1.240") ?: "192.168.1.240",
            prefs.getString("port", "554") ?: "554",
            prefs.getString("user", "") ?: "",
            prefs.getString("pass", "") ?: "",
            prefs.getString("path", "/cam/realmonitor?channel=%d&subtype=%d")
                ?: "/cam/realmonitor?channel=%d&subtype=%d"
        )
        layoutCount = prefs.getInt("layout", 4)
    }

    private fun saveConfig() {
        prefs.edit()
            .putString("host", config.host)
            .putString("port", config.port)
            .putString("user", config.username)
            .putString("pass", config.password)
            .putString("path", config.path)
            .putInt("layout", layoutCount)
            .apply()
    }

    private fun showHome() {
        stopAll()
        tiles.clear()
        fullScreen = false
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 15, 25))
            setPadding(18, 12, 18, 12)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "SMART CCTV VIEWER"
            textSize = 22f
            setTextColor(Color.WHITE)
        }
        header.addView(title, LinearLayout.LayoutParams(0, 54, 1f))
        val settings = button("DVR SETTINGS") { showSettings() }
        header.addView(settings)
        outer.addView(header)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf(1, 4, 6, 8).forEach { count ->
            controls.addView(button("${count} VIEW") {
                layoutCount = count
                saveConfig()
                connectLayout()
            })
        }
        controls.addView(button("CONNECT") { connectLayout() })
        outer.addView(controls)

        status = TextView(this).apply {
            text = "DVR: ${config.host}  |  RTSP: ${config.port}  |  Select a layout"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(4, 8, 4, 8)
        }
        outer.addView(status)

        grid = GridLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            columnCount = if (layoutCount == 6) 3 else if (layoutCount == 8) 4 else if (layoutCount == 1) 1 else 2
            rowCount = if (layoutCount == 8 || layoutCount == 6) 2 else if (layoutCount == 1) 1 else 2
            useDefaultMargins = true
        }
        outer.addView(grid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root = outer
        setContentView(outer)
        addTiles(layoutCount)
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 12f
            setOnClickListener { action() }
        }

    private fun addTiles(count: Int) {
        val container = grid ?: return
        container.removeAllViews()
        tiles.clear()
        val columns = when (count) { 1 -> 1; 6 -> 3; 8 -> 4; else -> 2 }
        container.columnCount = columns
        container.rowCount = if (count == 1) 1 else 2
        for (i in 0 until count) {
            val frame = FrameLayout(this).apply {
                setBackgroundColor(Color.rgb(20, 28, 38))
                isFocusable = true
                isFocusableInTouchMode = true
                tag = i
                setOnFocusChangeListener { v, hasFocus ->
                    v.setBackgroundColor(if (hasFocus) Color.rgb(20, 100, 160) else Color.rgb(20, 28, 38))
                    if (hasFocus) selected = i
                }
                setOnClickListener { selected = i; enterFullScreen(i) }
            }
            val video = VLCVideoLayout(this)
            frame.addView(video, FrameLayout.LayoutParams(-1, -1))
            val label = TextView(this).apply {
                text = "CAMERA ${i + 1}"
                setTextColor(Color.WHITE)
                textSize = 12f
                setBackgroundColor(0x99000000.toInt())
                setPadding(8, 4, 8, 4)
            }
            frame.addView(label, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ))
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(i % columns, 1f)
                rowSpec = GridLayout.spec(i / columns, 1f)
                setMargins(3, 3, 3, 3)
            }
            container.addView(frame, lp)
            tiles.add(Tile(frame, video, channel = i + 1))
        }
        if (tiles.isNotEmpty()) tiles[0].frame.requestFocus()
    }

    private fun streamUrl(channel: Int, main: Boolean): String {
        val user = Uri.encode(config.username)
        val pass = Uri.encode(config.password)
        val path = String.format(config.path, channel, if (main) 0 else 1)
        return "rtsp://$user:$pass@${config.host}:${config.port}$path"
    }

    private fun startTile(tile: Tile, main: Boolean = false) {
        if (config.username.isBlank()) {
            status?.text = "Open DVR SETTINGS and enter your DVR username/password."
            return
        }
        try {
            val lib = LibVLC(this, arrayListOf("--rtsp-tcp", "--network-caching=350", "--no-audio"))
            val player = MediaPlayer(lib)
            player.attachViews(tile.video, null, false, false)
            val media = Media(lib, Uri.parse(streamUrl(tile.channel, main)))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":rtsp-tcp")
            media.addOption(":network-caching=350")
            player.media = media
            media.release()
            tile.vlc = lib
            tile.player = player
            player.play()
        } catch (e: Exception) {
            status?.text = "Could not start camera ${tile.channel}. Check DVR credentials/stream path."
        }
    }

    private fun connectLayout() {
        if (fullScreen) showHome()
        tiles.forEach { releaseTile(it) }
        addTiles(layoutCount)
        tiles.forEach { startTile(it, false) }
        status?.text = "Connecting to ${config.host}. Multi-view uses Sub Stream."
        saveConfig()
    }

    private fun enterFullScreen(index: Int) {
        if (index !in tiles.indices) return
        savedFocus = index
        val channel = tiles[index].channel
        fullScreen = true
        tiles.forEach { releaseTile(it) }
        val container = grid ?: return
        container.removeAllViews()
        container.columnCount = 1
        container.rowCount = 1
        val tile = Tile(FrameLayout(this), VLCVideoLayout(this), channel = channel)
        tile.frame.addView(tile.video, FrameLayout.LayoutParams(-1, -1))
        container.addView(tile.frame, GridLayout.LayoutParams().apply {
            width = 0; height = 0
            columnSpec = GridLayout.spec(0, 1f); rowSpec = GridLayout.spec(0, 1f)
        })
        tiles.clear()
        tiles.add(tile)
        startTile(tile, true)
        status?.text = "FULL SCREEN · CAMERA $channel · BACK to return"
    }

    private fun leaveFullScreen() {
        if (!fullScreen) return
        fullScreen = false
        showHome()
        connectLayout()
        selected = savedFocus.coerceAtMost(tiles.lastIndex)
        tiles.getOrNull(selected)?.frame?.requestFocus()
    }

    private fun showSettings() {
        val fields = listOf(
            field("DVR IP address", config.host),
            field("RTSP port", config.port),
            field("DVR username", config.username),
            field("DVR password", config.password, true),
            field("RTSP path template", config.path)
        )
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 8, 20, 8)
        }
        fields.forEach { (label, edit) ->
            panel.addView(TextView(this).apply { text = label; setTextColor(Color.WHITE) })
            panel.addView(edit)
        }
        AlertDialog.Builder(this)
            .setTitle("DVR SETTINGS")
            .setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("SAVE") { _, _ ->
                config.host = fields[0].second.text.toString().trim()
                config.port = fields[1].second.text.toString().trim()
                config.username = fields[2].second.text.toString()
                config.password = fields[3].second.text.toString()
                config.path = fields[4].second.text.toString().trim()
                saveConfig()
                status?.text = "Settings saved. Press CONNECT."
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun field(label: String, value: String, secret: Boolean = false): Pair<String, EditText> {
        val edit = EditText(this).apply {
            setText(value)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.DKGRAY)
            hint = label
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        else InputType.TYPE_CLASS_TEXT
        }
        return label to edit
    }

    private fun releaseTile(tile: Tile) {
        try { tile.player?.stop() } catch (_: Exception) {}
        try { tile.player?.detachViews() } catch (_: Exception) {}
        try { tile.player?.release() } catch (_: Exception) {}
        try { tile.vlc?.release() } catch (_: Exception) {}
        tile.player = null
        tile.vlc = null
    }

    private fun stopAll() {
        tiles.forEach { releaseTile(it) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (fullScreen && keyCode == KeyEvent.KEYCODE_BACK) {
            leaveFullScreen()
            return true
        }
        if (!fullScreen) {
            when (keyCode) {
                KeyEvent.KEYCODE_1 -> { layoutCount = 1; connectLayout(); return true }
                KeyEvent.KEYCODE_4 -> { layoutCount = 4; connectLayout(); return true }
                KeyEvent.KEYCODE_6 -> { layoutCount = 6; connectLayout(); return true }
                KeyEvent.KEYCODE_8 -> { layoutCount = 8; connectLayout(); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    enterFullScreen(selected); return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }
}
