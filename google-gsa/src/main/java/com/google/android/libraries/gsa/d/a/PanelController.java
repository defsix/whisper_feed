package com.google.android.libraries.gsa.d.a;

public interface PanelController {
    void setPanelPosition(float position);

    void onPanelDragged();

    void startPanelDrag();

    void openPanel();

    void closePanel();

    boolean canInterceptTouchEvents();

    void setPanelEnabled(boolean enabled);
}
