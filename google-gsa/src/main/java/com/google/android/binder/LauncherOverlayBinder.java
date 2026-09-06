package com.google.android.binder;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LauncherOverlayBinder extends Binder implements IInterface {
    public IBinder asBinder() {
        return this;
    }

    /**
     * Handles incoming IPC transactions.
     *
     * @param code   The transaction code.
     * @param data   The input Parcel containing transaction data.
     * @param reply  The output Parcel for the response.
     * @param flags  Additional flags for the transaction.
     * @return True if the transaction was handled, false otherwise.
     * @throws RemoteException If an error occurs during transaction processing.
     */
    @Override
    protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags)
            throws RemoteException {
        // Handle high transaction codes by delegating to the superclass
        if (code > 16777215) {
            return super.onTransact(code, data, reply, flags);
        }

        // Enforce interface descriptor to ensure correct client
        try {
            data.enforceInterface(getInterfaceDescriptor());
            return false;
        } catch (SecurityException e) {
            // Log the error or handle it appropriately
            throw new RemoteException("Interface descriptor mismatch: " + e.getMessage());
        }
    }
}
