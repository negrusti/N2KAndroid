package com.gregor.n2kandroid

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.gregor.n2kandroid.mastervolt.MastervoltHidGateway

class MainActivity : Activity() {
    private lateinit var usbManager: UsbManager
    private lateinit var gateway: MastervoltHidGateway
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var connectButton: Button
    private lateinit var startButton: Button
    private lateinit var requestButton: Button
    private var capturedCount = 0

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device = intent.getParcelableExtraCompat<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                        openDevice(device)
                    } else {
                        setStatus("USB permission denied.")
                    }
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> connectOrRequestPermission()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    gateway.close()
                    setStatus("Mastervolt gateway detached.")
                    updateButtons()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        gateway = MastervoltHidGateway(usbManager)
        createUi()
        registerUsbReceiver()
        connectOrRequestPermission()
    }

    override fun onDestroy() {
        unregisterReceiver(usbReceiver)
        gateway.close()
        super.onDestroy()
    }

    private fun createUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        statusText = TextView(this).apply {
            textSize = 16f
            text = "Looking for Mastervolt HID gateway..."
        }
        root.addView(statusText)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }

        connectButton = Button(this).apply {
            text = "Connect"
            setOnClickListener { connectOrRequestPermission() }
        }
        controls.addView(connectButton)

        startButton = Button(this).apply {
            text = "Start"
            setOnClickListener {
                if (gateway.isCapturing) {
                    stopCapture()
                } else {
                    startCapture()
                }
            }
        }
        controls.addView(startButton)

        requestButton = Button(this).apply {
            text = "Request Info"
            setOnClickListener { requestProductInformation() }
        }
        controls.addView(requestButton)

        Button(this).apply {
            text = "Clear"
            setOnClickListener {
                capturedCount = 0
                logText.text = ""
                updateButtons()
            }
            controls.addView(this)
        }
        root.addView(controls)

        logText = TextView(this).apply {
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        root.addView(ScrollView(this).apply {
            addView(logText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        })

        setContentView(root)
        updateButtons()
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
    }

    private fun connectOrRequestPermission() {
        val device = gateway.findDevice()
        if (device == null) {
            setStatus("No Mastervolt HID gateway detected.")
            updateButtons()
            return
        }

        if (usbManager.hasPermission(device)) {
            openDevice(device)
            return
        }

        setStatus("Requesting USB permission...")
        usbManager.requestPermission(device, permissionIntent())
    }

    private fun openDevice(device: UsbDevice) {
        try {
            gateway.open(device)
            setStatus("Connected to Mastervolt HID gateway.")
        } catch (ex: Exception) {
            setStatus("Connect failed: ${ex.message}")
        }
        updateButtons()
    }

    private fun startCapture() {
        try {
            gateway.startCapture(
                onFrame = { frame ->
                    runOnUiThread {
                        capturedCount += 1
                        appendLog("#$capturedCount ${frame.summary()}")
                        updateButtons()
                    }
                },
                onError = { message ->
                    runOnUiThread {
                        setStatus("Capture error: $message")
                        updateButtons()
                    }
                },
            )
            setStatus("Capturing NMEA 2000 frames...")
        } catch (ex: Exception) {
            setStatus("Start failed: ${ex.message}")
        }
        updateButtons()
    }

    private fun stopCapture() {
        gateway.stopCapture()
        setStatus("Capture stopped.")
        updateButtons()
    }

    private fun requestProductInformation() {
        try {
            gateway.requestProductInformation()
            appendLog("Sent ISO Request for PGN 126996 Product Information.")
        } catch (ex: Exception) {
            setStatus("Request failed: ${ex.message}")
        }
    }

    private fun setStatus(message: String) {
        statusText.text = "$message Captured: $capturedCount"
    }

    private fun appendLog(line: String) {
        val current = logText.text.toString()
        logText.text = if (current.isEmpty()) line else "$line\n$current"
    }

    private fun updateButtons() {
        connectButton.isEnabled = true
        startButton.isEnabled = gateway.isOpen
        startButton.text = if (gateway.isCapturing) "Stop" else "Start"
        requestButton.isEnabled = gateway.isOpen
        statusText.visibility = View.VISIBLE
    }

    private fun permissionIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flags)
    }

    private inline fun <reified T> Intent.getParcelableExtraCompat(name: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.gregor.n2kandroid.USB_PERMISSION"
    }
}
