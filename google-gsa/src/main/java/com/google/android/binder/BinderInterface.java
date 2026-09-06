package com.google.android.binder;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.logging.Logger;

public class BinderInterface implements IInterface {
    private static final Logger LOGGER = Logger.getLogger(BinderInterface.class.getName());
    @NonNull
    private final String interfaceToken;
    @NonNull
    private final IBinder binder;
    public BinderInterface(@NonNull IBinder binder, @NonNull String interfaceToken) {
        this.binder = Objects.requireNonNull(binder, "Binder must not be null");
        this.interfaceToken = Objects.requireNonNull(interfaceToken, "Interface token must not be null");
    }

    @Override
    @NonNull
    public IBinder asBinder() {
        return this.binder;
    }

    @NonNull
    protected final Parcel createParcel() {
        Parcel obtain = Parcel.obtain();
        obtain.writeInterfaceToken(this.interfaceToken);
        return obtain;
    }

    /**
     * Performs a two-way transaction with the given transaction code and input Parcel.
     *
     * @param transactionCode The code identifying the transaction.
     * @param inputParcel    The input Parcel containing request data.
     * @return The reply Parcel, or null if the transaction fails.
     * @throws RemoteException If the IPC transaction fails.
     */
    @Nullable
    public Parcel transactWithReply(int transactionCode, @NonNull Parcel inputParcel) throws RemoteException {
        Objects.requireNonNull(inputParcel, "Input Parcel must not be null");
        Parcel replyParcel = Parcel.obtain();
        try {
            binder.transact(transactionCode, inputParcel, replyParcel, 0);
            replyParcel.readException(); // Throws RemoteException if the transaction failed
            return replyParcel;
        } catch (RemoteException | RuntimeException e) {
            LOGGER.warning("Transaction failed for code " + transactionCode + ": " + e.getMessage());
            replyParcel.recycle();
            throw e; // Propagate the exception to the caller
        } finally {
            inputParcel.recycle(); // Always recycle the input Parcel
        }
    }

    /**
     * Performs a one-way transaction with the given transaction code and input Parcel.
     * No reply is expected.
     *
     * @param transactionCode The code identifying the transaction.
     * @param inputParcel    The input Parcel containing request data.
     * @throws RemoteException If the IPC transaction fails.
     */
    protected void transactOneWay(int transactionCode, @NonNull Parcel inputParcel) throws RemoteException {
        Objects.requireNonNull(inputParcel, "Input Parcel must not be null");
        try {
            binder.transact(transactionCode, inputParcel, null, 1);
        } catch (RemoteException e) {
            LOGGER.warning("One-way transaction failed for code " + transactionCode + ": " + e.getMessage());
            throw e; // Propagate the exception to the caller
        } finally {
            inputParcel.recycle(); // Always recycle the input Parcel
        }
    }
}
