package com.gregor.n2kandroid

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.gregor.n2kandroid.nmea.BusDeviceRegistry
import com.gregor.n2kandroid.nmea.DeviceInfoDecoder
import com.gregor.n2kandroid.nmea.DiscoveredDevice
import com.gregor.n2kandroid.nmea.FastPacketAssembler
import com.gregor.n2kandroid.nmea.Nmea2000Frame

class MainActivity : Activity() {
    private lateinit var usbManager: UsbManager
    private lateinit var gateway: MastervoltHidGateway
    private lateinit var statusText: TextView
    private lateinit var countText: TextView
    private lateinit var deviceList: LinearLayout
    private lateinit var discoverButton: Button

    private val registry = BusDeviceRegistry()
    private val fastPackets = FastPacketAssembler()
    private var observedFrameCount = 0

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
            setPadding(dp(20), dp(28), dp(20), dp(16))
            setBackgroundColor(0xFFF5F7F9.toInt())
            fitsSystemWindows = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setOnApplyWindowInsetsListener { view, insets ->
                    val topInset = insets.systemWindowInsetTop
                    view.setPadding(dp(20), topInset + dp(16), dp(20), dp(16))
                    insets
                }
                requestApplyInsets()
            }
        }

        root.addView(TextView(this).apply {
            text = "NMEA 2000 Devices"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF17202A.toInt())
        })

        statusText = TextView(this).apply {
            text = "Looking for Mastervolt HID gateway..."
            textSize = 15f
            setTextColor(0xFF3E4C59.toInt())
            setPadding(0, dp(6), 0, dp(6))
        }
        root.addView(statusText)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            setPadding(0, dp(8), 0, dp(12))
        }

        discoverButton = styledButton("Discover").apply {
            setOnClickListener {
                if (gateway.isCapturing) {
                    requestDeviceInformation()
                } else {
                    startDiscovery(clearExisting = true)
                }
            }
        }
        controls.addView(discoverButton)
        root.addView(controls)

        countText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF52616B.toInt())
            text = "0 devices  |  0 frames observed"
        }
        root.addView(countText)

        deviceList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        root.addView(ScrollView(this).apply {
            addView(deviceList)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        })

        setContentView(root)
        renderDevices(emptyList())
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
            setStatus("Attach the Mastervolt USB gateway to connect automatically.")
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
            setStatus("Connected. Discovery is ready.")
        } catch (ex: Exception) {
            setStatus("Connect failed: ${ex.message}")
        }
        updateButtons()
    }

    private fun startDiscovery(clearExisting: Boolean) {
        if (clearExisting) {
            registry.clear()
            fastPackets.clear()
            observedFrameCount = 0
            renderDevices(emptyList())
        }

        try {
            gateway.startCapture(
                onFrame = { frame -> runOnUiThread { handleFrame(frame) } },
                onError = { message ->
                    runOnUiThread {
                        setStatus("Discovery error: $message")
                        updateButtons()
                    }
                },
            )
            requestDeviceInformation()
            setStatus("Listening for devices and collecting identity data...")
        } catch (ex: Exception) {
            setStatus("Discovery failed: ${ex.message}")
        }
        updateButtons()
    }

    private fun requestDeviceInformation() {
        try {
            gateway.requestAddressClaim()
            gateway.requestProductInformation()
            setStatus("Requested address claims and product information.")
        } catch (ex: Exception) {
            setStatus("Request failed: ${ex.message}")
        }
    }

    private fun handleFrame(frame: Nmea2000Frame) {
        if (frame.source == REQUEST_SOURCE_ADDRESS) {
            return
        }

        observedFrameCount += 1
        var devices = registry.updateFromFrame(frame)

        when (frame.pgn) {
            DeviceInfoDecoder.ADDRESS_CLAIM_PGN -> {
                DeviceInfoDecoder.decodeAddressClaim(
                    address = frame.source,
                    payload = frame.payload,
                    nowMillis = frame.timestampMillis,
                )?.let { devices = registry.merge(it) }
            }

            DeviceInfoDecoder.PRODUCT_INFORMATION_PGN -> {
                val productPayload = fastPackets.process(frame)
                if (productPayload != null) {
                    DeviceInfoDecoder.decodeProductInformation(
                        address = frame.source,
                        payload = productPayload,
                        nowMillis = frame.timestampMillis,
                    )?.let { devices = registry.merge(it) }
                }
            }
        }

        renderDevices(devices)
        updateButtons()
    }

    private fun renderDevices(devices: List<DiscoveredDevice>) {
        deviceList.removeAllViews()

        if (devices.isEmpty()) {
            deviceList.addView(TextView(this).apply {
                text = "No bus devices discovered yet."
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(0xFF52616B.toInt())
                setPadding(0, dp(48), 0, 0)
            })
        } else {
            devices.forEach { deviceList.addView(deviceRow(it)) }
        }

        countText.text = "${devices.size} devices  |  $observedFrameCount frames observed"
    }

    private fun deviceRow(device: DiscoveredDevice): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(0xFFFFFFFF.toInt(), 0xFFE0E6EB.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(10)
            }

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(TextView(context).apply {
                    text = "%03d".format(device.address)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(0xFFFFFFFF.toInt())
                    background = roundedBackground(0xFF0B6E69.toInt(), 0)
                    layoutParams = LinearLayout.LayoutParams(dp(56), dp(38))
                })

                addView(TextView(context).apply {
                    text = device.displayName
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xFF17202A.toInt())
                    setPadding(dp(12), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            })

            addView(TextView(context).apply {
                text = device.details
                textSize = 13f
                setTextColor(0xFF52616B.toInt())
                setPadding(0, dp(8), 0, 0)
            })

            addView(TextView(context).apply {
                text = secondaryDetails(device)
                textSize = 13f
                setTextColor(0xFF52616B.toInt())
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun secondaryDetails(device: DiscoveredDevice): String {
        val identity = listOfNotNull(
            device.softwareVersion?.let { "SW $it" },
            device.modelVersion?.let { "Model version $it" },
            device.serialCode?.let { "Serial $it" },
            device.nmeaVersion?.takeIf { it.isNotBlank() }?.let { "NMEA $it" },
            device.loadEquivalency?.let { "LEN $it" },
        ).joinToString("  |  ")

        return identity.ifBlank { "Frames observed: ${device.frameCount}" }
    }

    private fun styledButton(label: String): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            minHeight = dp(44)
            minimumHeight = dp(44)
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(46),
            ).apply {
                rightMargin = dp(8)
            }
        }
    }

    private fun roundedBackground(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(fill)
            if (stroke != 0) {
                setStroke(dp(1), stroke)
            }
        }
    }

    private fun setStatus(message: String) {
        statusText.text = message
    }

    private fun updateButtons() {
        discoverButton.isEnabled = gateway.isOpen
        discoverButton.text = if (gateway.isCapturing) "Refresh" else "Discover"
        statusText.visibility = View.VISIBLE
    }

    private fun permissionIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flags)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
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
        private const val REQUEST_SOURCE_ADDRESS = 254
    }
}
