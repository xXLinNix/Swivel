package io.github.xxlinnix.swivel.data.virtualpad

import android.os.Binder
import android.os.Parcel
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import io.github.xxlinnix.swivel.core.virtualpad.PadServiceContract
import io.github.xxlinnix.swivel.core.virtualpad.Uhid
import io.github.xxlinnix.swivel.core.virtualpad.XboxPadReport
import java.io.FileDescriptor
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Runs in a separate process that Shizuku starts as the shell user, the same privilege
 * `adb shell` has. That user may open /dev/uhid, where an ordinary app may not, and so
 * can create a HID gamepad that Android and every game treat as real. scrcpy's
 * `--gamepad=uhid` works the same way.
 *
 * It does one thing: hold one gamepad device open and write the reports Swivel sends.
 * Swivel's app process talks to it over Binder ([PadServiceContract]). Shizuku
 * constructs this class by reflection, so the constructor must stay (see proguard-rules.pro).
 */
class VirtualPadUserService() : Binder() {
    private var fd: FileDescriptor? = null

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            PadServiceContract.TX_OPEN -> {
                data.enforceInterface(PadServiceContract.DESCRIPTOR)
                val error = open(data.readString() ?: PadServiceContract.DEVICE_NAME)
                reply?.writeNoException()
                reply?.writeString(error)
            }
            PadServiceContract.TX_SEND -> {
                data.enforceInterface(PadServiceContract.DESCRIPTOR)
                data.createByteArray()?.let { send(it) }
            }
            PadServiceContract.TX_CLOSE -> {
                data.enforceInterface(PadServiceContract.DESCRIPTOR)
                close()
                reply?.writeNoException()
            }
            PadServiceContract.TX_DESTROY -> {
                close()
                exitProcess(0)
            }
            else -> return super.onTransact(code, data, reply, flags)
        }
        return true
    }

    @Synchronized
    private fun open(name: String): String? {
        if (fd != null) return null
        return try {
            val opened = Os.open(Uhid.DEVICE_PATH, OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)
            val create = Uhid.create2(
                name = name,
                phys = PadServiceContract.PHYS,
                uniq = "",
                descriptor = XboxPadReport.DESCRIPTOR,
                bus = Uhid.BUS_VIRTUAL,
                vendor = XboxPadReport.VENDOR_ID,
                product = XboxPadReport.PRODUCT_ID,
            )
            Os.write(opened, create, 0, create.size)
            fd = opened
            drain(opened)
            Log.i(TAG, "virtual gamepad created")
            null
        } catch (e: ErrnoException) {
            Log.w(TAG, "could not create the virtual gamepad", e)
            "${Uhid.DEVICE_PATH}: ${e.message}"
        }
    }

    @Synchronized
    private fun send(report: ByteArray) {
        val current = fd ?: return
        try {
            val event = Uhid.input2(report)
            Os.write(current, event, 0, event.size)
        } catch (e: ErrnoException) {
            Log.w(TAG, "report failed", e)
        }
    }

    /** Closing the file removes the device, and games see the gamepad disconnect. */
    @Synchronized
    private fun close() {
        val current = fd ?: return
        fd = null
        try {
            val destroy = Uhid.destroy()
            Os.write(current, destroy, 0, destroy.size)
        } catch (e: ErrnoException) {
        }
        try { Os.close(current) } catch (e: ErrnoException) { }
        Log.i(TAG, "virtual gamepad removed")
    }

    /**
     * The kernel queues events for us (start, open, close, output). Nothing here needs
     * them, but an unread queue fills up, so they are read and dropped, as scrcpy does.
     */
    private fun drain(descriptor: FileDescriptor) {
        thread(name = "uhid-drain", isDaemon = true) {
            val buffer = ByteArray(Uhid.CREATE2_HEADER_SIZE + Uhid.MAX_DATA)
            try {
                while (true) {
                    if (Os.read(descriptor, buffer, 0, buffer.size) <= 0) return@thread
                }
            } catch (e: ErrnoException) {
                // Closed.
            }
        }
    }

    private companion object {
        const val TAG = "SwivelPad"
    }
}
