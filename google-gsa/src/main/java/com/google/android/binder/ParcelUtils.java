package com.google.android.binder;

import android.os.IInterface;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ParcelUtils {
    private ParcelUtils() {
        throw new AssertionError("Utility class, not instantiable");
    }

    /**
     * Reads a boolean value from the given {@link Parcel}.
     *
     * @param parcel The {@link Parcel} to read from.
     * @return {@code true} if the parcel contains 1, {@code false} otherwise.
     */
    public static boolean readBoolean(@NonNull Parcel parcel) {
        return parcel.readInt() == 1;
    }
    public static void writeBoolean(Parcel parcel, boolean z) {
        parcel.writeInt(z ? 1 : 0);
    }

    /**
     * Reads a {@link Parcelable} object from the given {@link Parcel} using the specified {@link Creator}.
     *
     * @param <T>     The type of the {@link Parcelable}.
     * @param parcel  The {@link Parcel} to read from.
     * @param creator The {@link Creator} to instantiate the {@link Parcelable}.
     * @return The {@link Parcelable} object, or {@code null} if the parcel indicates no object.
     */
    @Nullable
    public static <T extends Parcelable> T readParcelable(@NonNull Parcel parcel, @NonNull Creator<T> creator) {
        return parcel.readInt() == 0 ? null : creator.createFromParcel(parcel);
    }
    /**
     * Writes a {@link Parcelable} object to the given {@link Parcel}.
     *
     * @param parcel     The {@link Parcel} to write to.
     * @param parcelable The {@link Parcelable} object to write, or {@code null}.
     */
    public static void writeParcelable(@NonNull Parcel parcel, @Nullable Parcelable parcelable) {
        if (parcelable == null) {
            parcel.writeInt(0);
            return;
        }
        parcel.writeInt(1);
        parcelable.writeToParcel(parcel, 0);
    }

    /**
     * Writes an {@link IInterface} object to the given {@link Parcel}.
     *
     * @param parcel     The {@link Parcel} to write to.
     * @param iInterface The {@link IInterface} object to write, or {@code null}.
     */
    public static void writeInterface(@NonNull Parcel parcel, @Nullable IInterface iInterface) {
        parcel.writeStrongBinder(iInterface != null ? iInterface.asBinder() : null);
    }
}
