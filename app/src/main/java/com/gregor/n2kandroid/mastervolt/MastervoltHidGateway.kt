package com.gregor.n2kandroid.mastervolt

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.os.Build
import com.gregor.n2kandroid.nmea.Nmea2000Frame
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class MastervoltHidGateway(private val usbManager: UsbManager) : AutoCloseable {
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var readerThread: Thread? = null
    private var readRequest: UsbRequest? = null
    private val running = AtomicBoolean(false)

    val isOpen: Boolean
        get() = connection != null

    val isCapturing: Boolean
        get() = running.get()

    fun findDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull(::isMastervoltDevice)
    }

    fun open(device: UsbDevice) {
        close()
        require(isMastervoltDevice(device)) {
            "Unexpected USB device ${device.vendorId}:${device.productId}"
        }

        val selectedInterface = findHidInterface(device)
            ?: throw IllegalStateException("Mastervolt HID interface was not found.")
        val endpoints = findEndpoints(selectedInterface)
        val openedConnection = usbManager.openDevice(device)
            ?: throw IllegalStateException("Could not open USB device. Check Android USB permission.")

        if (!openedConnection.claimInterface(selectedInterface, true)) {
            openedConnection.close()
            throw IllegalStateException("Could not claim Mastervolt HID interface.")
        }

        connection = openedConnection
        usbInterface = selectedInterface
        inEndpoint = endpoints.first
        outEndpoint = endpoints.second
    }

    fun startCapture(onFrame: (Nmea2000Frame) -> Unit, onError: (String) -> Unit) {
        val activeConnection = connection ?: throw IllegalStateException("Gateway is not open.")
        val endpoint = inEndpoint ?: throw IllegalStateException("Interrupt IN endpoint was not found.")
        if (!running.compareAndSet(false, true)) return

        readerThread = Thread({
            val request = UsbRequest()
            try {
                if (!request.initialize(activeConnection, endpoint)) {
                    onError("Could not initialize USB read request.")
                    return@Thread
                }
                readRequest = request

                while (running.get()) {
                    val buffer = ByteBuffer.allocate(
                        if (endpoint.maxPacketSize > MastervoltProtocol.HID_PAYLOAD_SIZE) {
                            endpoint.maxPacketSize
                        } else {
                            MastervoltProtocol.HID_PAYLOAD_SIZE
                        },
                    )
                    if (!request.queueCompat(buffer, buffer.capacity())) {
                        onError("Could not queue USB read request.")
                        break
                    }

                    val completed = activeConnection.requestWait()
                    if (completed !== request) {
                        continue
                    }

                    val bytesRead = buffer.position()
                    val raw = buffer.array()
                    val now = System.currentTimeMillis()
                    MastervoltProtocol.decodeReport(raw, bytesRead, now).forEach(onFrame)
                }
            } catch (ex: Exception) {
                if (running.get()) {
                    onError(ex.message ?: ex.javaClass.simpleName)
                }
            } finally {
                readRequest = null
                request.close()
            }
        }, "mastervolt-hid-reader")
        readerThread?.start()
    }

    fun stopCapture() {
        running.set(false)
        readRequest?.cancel()
        readerThread?.interrupt()
        readerThread = null
    }

    fun requestProductInformation() {
        writeReport(MastervoltProtocol.encodeProductInformationRequest(includeReportId = false))
    }

    fun writeReport(report: ByteArray) {
        val activeConnection = connection ?: throw IllegalStateException("Gateway is not open.")
        if (writeReportViaControlTransfer(activeConnection, report)) {
            return
        }

        val endpoint = outEndpoint ?: throw IllegalStateException("No interrupt OUT endpoint is available.")
        if (running.get()) {
            throw IllegalStateException("Endpoint writes are disabled while capture is running.")
        }

        runEndpointWrite(activeConnection, endpoint, report)
    }

    private fun runEndpointWrite(
        activeConnection: UsbDeviceConnection,
        endpoint: UsbEndpoint,
        report: ByteArray,
    ) {
        val written = activeConnection.bulkTransfer(endpoint, report, report.size, WRITE_TIMEOUT_MS)
        if (written >= 0) {
            return
        }

        if (!running.get()) {
            val request = UsbRequest()
            try {
                if (!request.initialize(activeConnection, endpoint)) {
                    throw IllegalStateException("Could not initialize USB write request.")
                }
                val buffer = ByteBuffer.wrap(report)
                if (!request.queueCompat(buffer, report.size)) {
                    throw IllegalStateException("Could not queue USB write request.")
                }
                activeConnection.requestWait()
            } finally {
                request.close()
            }
        }
    }

    private fun writeReportViaControlTransfer(
        activeConnection: UsbDeviceConnection,
        report: ByteArray,
    ): Boolean {
        if (tryControlTransfer(activeConnection, report)) {
            return true
        }

        val reportWithId = ByteArray(MastervoltProtocol.HID_REPORT_BUFFER_SIZE)
        if (report.size == MastervoltProtocol.HID_REPORT_BUFFER_SIZE) {
            report.copyInto(reportWithId)
        } else {
            report.copyInto(reportWithId, destinationOffset = 1)
        }
        return tryControlTransfer(activeConnection, reportWithId)
    }

    private fun tryControlTransfer(
        activeConnection: UsbDeviceConnection,
        report: ByteArray,
    ): Boolean {
        val result = activeConnection.controlTransfer(
            UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_CLASS or USB_RECIP_INTERFACE,
            HID_SET_REPORT,
            HID_REPORT_TYPE_OUTPUT shl 8,
            usbInterface?.id ?: 0,
            report,
            report.size,
            WRITE_TIMEOUT_MS,
        )
        return result >= 0
    }

    override fun close() {
        stopCapture()
        val activeConnection = connection
        val activeInterface = usbInterface
        if (activeConnection != null && activeInterface != null) {
            activeConnection.releaseInterface(activeInterface)
        }
        activeConnection?.close()
        connection = null
        usbInterface = null
        inEndpoint = null
        outEndpoint = null
    }

    private fun isMastervoltDevice(device: UsbDevice): Boolean {
        return device.vendorId == MastervoltProtocol.VENDOR_ID &&
            device.productId == MastervoltProtocol.PRODUCT_ID
    }

    private fun findHidInterface(device: UsbDevice): UsbInterface? {
        for (index in 0 until device.interfaceCount) {
            val candidate = device.getInterface(index)
            if (candidate.interfaceClass == UsbConstants.USB_CLASS_HID) {
                return candidate
            }
        }
        return if (device.interfaceCount > 0) device.getInterface(0) else null
    }

    private fun findEndpoints(usbInterface: UsbInterface): Pair<UsbEndpoint?, UsbEndpoint?> {
        var inEndpoint: UsbEndpoint? = null
        var outEndpoint: UsbEndpoint? = null
        for (index in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(index)
            if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_INT) continue
            if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                inEndpoint = endpoint
            } else if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                outEndpoint = endpoint
            }
        }
        return inEndpoint to outEndpoint
    }

    companion object {
        private const val HID_SET_REPORT = 0x09
        private const val HID_REPORT_TYPE_OUTPUT = 0x02
        private const val USB_RECIP_INTERFACE = 0x01
        private const val WRITE_TIMEOUT_MS = 1_000
    }
}

private fun UsbRequest.queueCompat(buffer: ByteBuffer, length: Int): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        queue(buffer)
    } else {
        @Suppress("DEPRECATION")
        queue(buffer, length)
    }
}
