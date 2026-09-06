package com.google.android.libraries.launcherclient

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

interface ILauncherOverlayCallback : IInterface {
    @Throws(RemoteException::class)
    fun overlayScrollChanged(progress: Float)

    @Throws(RemoteException::class)
    fun overlayStatusChanged(status: Int)

    class Stub : Binder(), ILauncherOverlayCallback {
        init {
            attachInterface(this, ILauncherOverlayCallback::class.java.name)
        }

        override fun asBinder(): IBinder {
            return this
        }

        @Throws(RemoteException::class)
        override fun overlayScrollChanged(progress: Float) {
        }

        @Throws(RemoteException::class)
        override fun overlayStatusChanged(status: Int) {
        }

        @Throws(RemoteException::class)
        public override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(ILauncherOverlay::class.java.name)
                    return true
                }

                OVERLAY_SCROLL_CHANGED_TRANSACTION -> {
                    data.enforceInterface(ILauncherOverlayCallback::class.java.name)
                    overlayScrollChanged(data.readFloat())
                    return true
                }

                OVERLAY_STATUS_CHANGED_TRANSACTION -> {
                    data.enforceInterface(ILauncherOverlayCallback::class.java.name)
                    overlayStatusChanged(data.readInt())
                    return super.onTransact(code, data, reply, flags)
                }

                else -> return super.onTransact(code, data, reply, flags)
            }
        }

        private class Proxy(private val mRemote: IBinder) : ILauncherOverlayCallback {
            override fun asBinder(): IBinder {
                return mRemote
            }

            @Throws(RemoteException::class)
            override fun overlayScrollChanged(progress: Float) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ILauncherOverlayCallback::class.java.name)
                    data.writeFloat(progress)

                    mRemote.transact(OVERLAY_SCROLL_CHANGED_TRANSACTION, data, null, FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun overlayStatusChanged(status: Int) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ILauncherOverlayCallback::class.java.name)
                    data.writeInt(status)

                    mRemote.transact(OVERLAY_STATUS_CHANGED_TRANSACTION, data, null, FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }
        }

        companion object {
            const val OVERLAY_SCROLL_CHANGED_TRANSACTION: Int = 1
            const val OVERLAY_STATUS_CHANGED_TRANSACTION: Int = 2

            fun asInterface(obj: IBinder?): ILauncherOverlayCallback? {
                if (obj == null) {
                    return null
                }

                val iin = obj.queryLocalInterface(ILauncherOverlayCallback::class.java.getName())
                return if (iin != null && iin is ILauncherOverlayCallback) {
                    iin
                } else {
                    Proxy(obj)
                }
            }
        }
    }
}