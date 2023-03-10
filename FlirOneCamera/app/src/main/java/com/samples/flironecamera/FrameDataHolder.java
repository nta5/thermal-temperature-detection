/*******************************************************************
 * @title FLIR THERMAL SDK
 * @file FrameDataHolder.java
 * @Author FLIR Systems AB
 *
 * @brief Container class that holds references to Bitmap images
 *
 * Copyright 2019:    FLIR Systems
 ********************************************************************/

package com.samples.flironecamera;

import android.graphics.Bitmap;
import android.widget.TextView;

class FrameDataHolder {

    public static Bitmap msxBitmap = null;
    public static Bitmap dcBitmap = null;
    public static SocketHandler socketHandler = null;
    public static TextView tempHolder = null;

    FrameDataHolder(Bitmap msxBitmap, Bitmap dcBitmap, SocketHandler socketHandler, TextView tempHolder) {
        this.msxBitmap = msxBitmap;
        this.dcBitmap = dcBitmap;
        this.socketHandler = socketHandler;
        this.tempHolder = tempHolder;
        socketHandler.bitmapInit(msxBitmap, tempHolder);
    }

    public static boolean capture() {
        if (socketHandler == null || tempHolder == null) return false;
        socketHandler.capture();
        return true;
    }

    public static Bitmap getMsxBitmap() {
        return msxBitmap;
    }
}
