package com.google.android.libraries.launcherclient;

import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.NonNull;

import com.google.android.binder.LauncherOverlayBinder;
import com.google.android.binder.ParcelUtils;


public abstract class LauncherOverlayInterfaceBinder extends LauncherOverlayBinder implements ILauncherOverlay {
    private static final String INTERFACE_DESCRIPTOR = "com.google.android.libraries.launcherclient.ILauncherOverlay";
    private static final int TRANSACTION_START_SCROLL = 1;
    private static final int TRANSACTION_ON_SCROLL = 2;
    private static final int TRANSACTION_END_SCROLL = 3;
    private static final int TRANSACTION_WINDOW_ATTACHED_LAYOUT = 4;
    private static final int TRANSACTION_REQUEST_VOICE_DETECTION = 5;
    private static final int TRANSACTION_ON_PAUSE = 7;
    private static final int TRANSACTION_ON_RESUME = 8;
    private static final int TRANSACTION_OPEN_OVERLAY = 9;
    private static final int TRANSACTION_WINDOW_DETACHED = 10;
    private static final int TRANSACTION_IS_ENABLED = 13;
    private static final int TRANSACTION_WINDOW_ATTACHED_BUNDLE = 14;
    private static final int TRANSACTION_CLOSE_OVERLAY = 16;

    protected LauncherOverlayInterfaceBinder() {
        attachInterface(this, INTERFACE_DESCRIPTOR);
    }

    @Override
    public boolean onTransact(final int code, @NonNull final Parcel data, final Parcel reply, final int flags) throws RemoteException {
        if (super.onTransact(code, data, reply, flags)) return true;
        ILauncherOverlayCallback callback;
        IBinder binder;

        switch (code) {
            case TRANSACTION_START_SCROLL:
                startScroll();
                break;
            case TRANSACTION_ON_SCROLL:
                onScroll(data.readFloat());
                break;
            case TRANSACTION_END_SCROLL:
                endScroll();
                break;
            case TRANSACTION_WINDOW_ATTACHED_LAYOUT:
                LayoutParams params = ParcelUtils.readParcelable(data, LayoutParams.CREATOR);
                callback = getCallbackFromBinder(data.readStrongBinder());
                windowAttached(params, callback, data.readInt());
                break;
            case TRANSACTION_REQUEST_VOICE_DETECTION:
                requestVoiceDetection(ParcelUtils.readBoolean(data));
                break;
            case TRANSACTION_ON_PAUSE:
                onPause();
                break;
            case TRANSACTION_ON_RESUME:
                onResume();
                break;
            case TRANSACTION_OPEN_OVERLAY:
                openOverlay(data.readInt());
                break;
            case TRANSACTION_WINDOW_DETACHED:
                windowDetached(ParcelUtils.readBoolean(data));
                break;
            case 11:
                //Voice search is always enabled, so we don't need to check the version
                break;
            case 12:
                // This method is not used in the current implementation, but we keep it for compatibility
                break;
            case TRANSACTION_IS_ENABLED:
                reply.writeNoException();
                ParcelUtils.writeBoolean(reply, true);
                break;
            case TRANSACTION_WINDOW_ATTACHED_BUNDLE:
                Bundle bundle = ParcelUtils.readParcelable(data, Bundle.CREATOR);
                callback = getCallbackFromBinder(data.readStrongBinder());
                windowAttached(bundle, callback);
                break;
            case TRANSACTION_CLOSE_OVERLAY:
                closeOverlay(data.readInt());
                break;
            default:
                return false;
        }
        return true;
    }

    private ILauncherOverlayCallback getCallbackFromBinder(final IBinder binder) {
        if (binder == null) return null;

        IInterface iface = binder.queryLocalInterface("com.google.android.libraries.launcherclient.ILauncherOverlayCallback");
        return (iface instanceof ILauncherOverlayCallback)
                ? (ILauncherOverlayCallback) iface
                : new LauncherOverlayCallback(binder);
    }
}
