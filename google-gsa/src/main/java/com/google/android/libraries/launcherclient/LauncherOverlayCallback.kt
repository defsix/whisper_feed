package com.google.android.libraries.launcherclient

import android.os.IBinder
import android.os.RemoteException
import com.google.android.binder.BinderInterface
import java.util.logging.Logger


/**
 * A callback implementation for the launcher overlay, handling IPC transactions
 * to send overlay updates (progress and status) to a remote process.
 */
class LauncherOverlayCallback(binder: IBinder) :
    BinderInterface(binder, "com.google.android.libraries.launcherclient.ILauncherOverlayCallback"),
    ILauncherOverlayCallback {
    /**
     * Sends the overlay progress to the remote process.
     *
     * @param progress The progress value (e.g., a float between 0.0 and 1.0).
     * @throws RemoteException If the IPC transaction fails.
     */
    override fun overlayScrollChanged(progress: Float) {
        val parcel = createParcel()
        try {
            parcel.writeFloat(progress)
            transactOneWay(TRANSACTION_SET_PROGRESS, parcel)
        } catch (e: RemoteException) {
            LOGGER.warning("Failed to send overlay progress: " + progress + ", error: " + e.message)
        }
    }

    /**
     * Sends the overlay status to the remote process.
     *
     * @param status The status code to send.
     * @throws RemoteException If the IPC transaction fails.
     */
    override fun overlayStatusChanged(status: Int) {
        val parcel = createParcel()
        try {
            parcel.writeInt(status)
            transactOneWay(TRANSACTION_SET_STATUS, parcel)
        } catch (e: RemoteException) {
            LOGGER.warning("Failed to send overlay status: " + status + ", error: " + e.message)
        }
    }

    companion object {
        private val LOGGER: Logger = Logger.getLogger(LauncherOverlayCallback::class.java.getName())

        // Transaction codes for IPC calls
        private const val TRANSACTION_SET_PROGRESS = 1
        private const val TRANSACTION_SET_STATUS = 2
    }
}