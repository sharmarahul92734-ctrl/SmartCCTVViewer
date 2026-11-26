
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
        val label: TextView,
        val channel: Int,
        var video: VLCVideoLayout? = null,
        var vlc: LibVLC? = null,
        var player: MediaPlayer? = null
    )

    private val prefs by lazy {
        getSharedPreferences("cctv_private_settings", MODE_PRIVATE)
    }

    private var config = Config()
    private var grid: GridLayout? = null
    private var status: TextView? = null

    private val tiles = mutableListOf<Tile>()

    private var layoutCount = 4
    private var selected = 0
    private var fullScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        loadConfig()

        // VLC is NOT initialized here.
        showHome()
    }

    private fun loadConfig() {
        config = Config(
            prefs.getString("host", "192.168.1.240") ?: "192.168.1.240",
            prefs.getString("port", "554") ?: "554",
            prefs.getString("user", "") ?: "",
            prefs.getString("pass", "") ?: "",
            prefs.getString(
                "path",
                "/cam/realmonitor?channel=%d&subtype=%d"
            ) ?: "/cam/realmonitor?channel=%d&subtype=%d"
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
            setPadding(18, 12, 18, 12)
            setBackgroundColor(Color.rgb(8, 15, 25))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "SMART CCTV VIEWER"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(0, 55, 1f)
        )

        header.addView(button("DVR SETTINGS") {
            showSettings()
        })

        outer.addView(header)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        listOf(1, 4, 6, 8).forEach { count ->
            controls.addView(button("$count VIEW") {
                layoutCount = count
                saveConfig()
                connectLayout()
            })
        }

        controls.addView(button("CONNECT") {
            connectLayout()
        })

        outer.addView(controls)

        status = TextView(this).apply {
            text = "DVR: ${config.host} | Select DVR SETTINGS"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(5, 10, 5, 10)
        }

        outer.addView(status)

        grid = GridLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            useDefaultMargins = true
        }

        outer.addView(
            grid,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(outer)

        // Only normal Android views are created on startup.
        addTiles(layoutCount)

        status?.text = "Ready. Configure DVR and press CONNECT."
    }

    private fun button(
        textValue: String,
        action: () -> Unit
    ): Button {

        return Button(this).apply {
            text = textValue
            textSize = 12f
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { action() }
        }
    }

    private fun addTiles(count: Int) {
        val container = grid ?: return

        container.removeAllViews()
        tiles.clear()

        val columns = when (count) {
            1 -> 1
            6 -> 3
            8 -> 4
            else -> 2
        }

        container.columnCount = columns
        container.rowCount = if (count == 1) 1 else 2

        for (i in 0 until count) {

            val frame = FrameLayout(this).apply {
                setBackgroundColor(Color.rgb(20, 28, 38))
                isFocusable = true
                isFocusableInTouchMode = true

                setOnFocusChangeListener { view, focused ->
                    view.setBackgroundColor(
                        if (focused) Color.rgb(20, 100, 160)
                        else Color.rgb(20, 28, 38)
                    )

                    if (focused) selected = i
                }

                setOnClickListener {
                    selected = i
                    enterFullScreen(i)
                }
            }

            val label = TextView(this).apply {
                text = "CAMERA ${i + 1}"
                textSize = 14f
                setTextColor(Color.WHITE)
                setBackgroundColor(0x99000000.toInt())
                setPadding(10, 6, 10, 6)
            }

            frame.addView(
                label,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                )
            )

            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(i % columns, 1f)
                rowSpec = GridLayout.spec(i / columns, 1f)
                setMargins(3, 3, 3, 3)
            }

            container.addView(frame, lp)

            tiles.add(
                Tile(frame, label, i + 1)
            )
        }

        tiles.firstOrNull()?.frame?.requestFocus()
    }

    private fun streamUrl(channel: Int, main: Boolean): String {

        val user = Uri.encode(config.username)
        val pass = Uri.encode(config.password)

        val path = String.format(
            config.path,
            channel,
            if (main) 0 else 1
        )

        return "rtsp://$user:$pass@${config.host}:${config.port}$path"
    }

    // VLC starts only when the user presses CONNECT.
    private fun startTile(tile: Tile, main: Boolean = false) {

        if (config.username.isBlank()) {
            status?.text = "Enter DVR username in DVR SETTINGS."
            return
        }

        try {
            val video = VLCVideoLayout(this)

            tile.frame.addView(
                video,
                0,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            tile.video = video

            val lib = LibVLC(
                this,
                arrayListOf(
                    "--rtsp-tcp",
                    "--network-caching=350",
                    "--no-audio"
                )
            )

            val player = MediaPlayer(lib)

            player.attachViews(video, null, false, false)

            val media = Media(
                lib,
                Uri.parse(streamUrl(tile.channel, main))
            )

            media.setHWDecoderEnabled(true, false)
            media.addOption(":rtsp-tcp")
            media.addOption(":network-caching=350")

            player.media = media
            media.release()

            tile.vlc = lib
            tile.player = player

            player.play()

        } catch (e: Exception) {

            releaseTile(tile)

            status?.text =
                "Camera ${tile.channel} failed. Check DVR settings."

        } catch (e: LinkageError) {

            releaseTile(tile)

            status?.text =
                "VLC library could not load. Check APK libraries."
        }
    }

    private fun connectLayout() {

        if (config.username.isBlank()) {
            status?.text = "Please enter DVR username and password first."
            showSettings()
            return
        }

        stopAll()
        fullScreen = false

        addTiles(layoutCount)

        status?.text = "Connecting to ${config.host}..."

        tiles.toList().forEach {
            startTile(it, false)
        }

        saveConfig()
    }

    private fun enterFullScreen(index: Int) {

        if (index !in tiles.indices) return

        val channel = tiles[index].channel

        stopAll()
        fullScreen = true

        val container = grid ?: return

        container.removeAllViews()
        container.columnCount = 1
        container.rowCount = 1

        tiles.clear()

        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
        }

        val label = TextView(this).apply {
            text = "CAMERA $channel"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(10, 6, 10, 6)
            setBackgroundColor(0x99000000.toInt())
        }

        frame.addView(
            label,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            )
        )

        container.addView(
            frame,
            GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(0, 1f)
                rowSpec = GridLayout.spec(0, 1f)
            }
        )

        val tile = Tile(frame, label, channel)
        tiles.add(tile)

        startTile(tile, true)

        status?.text = "FULL SCREEN - CAMERA $channel - BACK to return"
    }

    private fun leaveFullScreen() {

        if (!fullScreen) return

        showHome()

        selected = selected.coerceAtMost(tiles.lastIndex)

        tiles.getOrNull(selected)?.frame?.requestFocus()

        status?.text = "Press CONNECT to start cameras."
    }

    private fun showSettings() {

        val host = EditText(this).apply {
            setText(config.host)
            hint = "DVR IP address"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val port = EditText(this).apply {
            setText(config.port)
            hint = "RTSP port"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val username = EditText(this).apply {
            setText(config.username)
            hint = "DVR username"
        }

        val password = EditText(this).apply {
            setText(config.password)
            hint = "DVR password"
            inputType =
                InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val path = EditText(this).apply {
            setText(config.path)
            hint = "RTSP path"
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 10, 20, 10)

            fun addField(title: String, edit: EditText) {
                addView(TextView(this@MainActivity).apply {
                    text = title
                    setTextColor(Color.BLACK)
                })
                addView(edit)
            }

            addField("DVR IP address", host)
            addField("RTSP port", port)
            addField("DVR username", username)
            addField("DVR password", password)
            addField("RTSP path template", path)
        }

        AlertDialog.Builder(this)
            .setTitle("DVR SETTINGS")
            .setView(
                ScrollView(this).apply {
                    addView(panel)
                }
            )
            .setPositiveButton("SAVE") { _, _ ->

                config.host = host.text.toString().trim()
                config.port = port.text.toString().trim()
                config.username = username.text.toString()
                config.password = password.text.toString()
                config.path = path.text.toString().trim()

                saveConfig()

                status?.text = "Settings saved. Press CONNECT."
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun releaseTile(tile: Tile) {

        try {
            tile.player?.stop()
        } catch (_: Exception) {}

        try {
            tile.player?.detachViews()
        } catch (_: Exception) {}

        try {
            tile.player?.release()
        } catch (_: Exception) {}

        try {
            tile.vlc?.release()
        } catch (_: Exception) {}

        tile.player = null
        tile.vlc = null
        tile.video = null
    }

    private fun stopAll() {
        tiles.forEach {
            releaseTile(it)
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?
    ): Boolean {

        if (fullScreen && keyCode == KeyEvent.KEYCODE_BACK) {
            leaveFullScreen()
            return true
        }

        if (!fullScreen) {

            when (keyCode) {

                KeyEvent.KEYCODE_1 -> {
                    layoutCount = 1
                    connectLayout()
                    return true
                }

                KeyEvent.KEYCODE_4 -> {
                    layoutCount = 4
                    connectLayout()
                    return true
                }

                KeyEvent.KEYCODE_6 -> {
                    layoutCount = 6
                    connectLayout()
                    return true
                }

                KeyEvent.KEYCODE_8 -> {
                    layoutCount = 8
                    connectLayout()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    enterFullScreen(selected)
                    return true
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
