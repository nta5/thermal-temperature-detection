/*******************************************************************
 * @title FLIR THERMAL SDK
 * @file CameraHandler.java
 * @Author FLIR Systems AB
 *
 * @brief Helper class that encapsulates *most* interactions with a FLIR ONE camera
 *
 * Copyright 2019:    FLIR Systems
 ********************************************************************/
package com.samples.flironecamera;

import android.graphics.Bitmap;
import android.util.Log;

import com.flir.thermalsdk.androidsdk.image.BitmapAndroid;
import com.flir.thermalsdk.image.TemperatureUnit;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.image.ThermalValue;
import com.flir.thermalsdk.live.Camera;
import com.flir.thermalsdk.live.CameraInformation;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.ConnectParameters;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.live.discovery.DiscoveryFactory;
import com.flir.thermalsdk.live.remote.Calibration;
import com.flir.thermalsdk.live.remote.RemoteControl;
import com.flir.thermalsdk.live.streaming.Stream;
import com.flir.thermalsdk.live.streaming.ThermalStreamer;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Objects;

/**
 * Encapsulates the handling of a FLIR ONE camera or built in emulator, discovery, connecting and start receiving images.
 * All listeners are called from Thermal SDK on a non-ui thread
 * <p/>
 * Usage:
 * <pre>
 * Start discovery of FLIR FLIR ONE cameras or built in FLIR ONE cameras emulators
 * {@linkplain #startDiscovery(DiscoveryEventListener, DiscoveryStatus)}
 * Use a discovered Camera {@linkplain Identity} and connect to the Camera
 * (note that calling connect is blocking and it is mandatory to call this function from a background thread):
 * {@linkplain #connect(Identity, ConnectionStatusListener)}
 * Once connected to a camera
 * {@linkplain #startStream(StreamDataListener)}
 * </pre>
 * <p/>
 * You don't *have* to specify your application to listen or USB intents but it might be beneficial for you application,
 * we are enumerating the USB devices during the discovery process which eliminates the need to listen for USB intents.
 * See the Android documentation about USB Host mode for more information
 * <p/>
 * Please note, this is <b>NOT</b> production quality code, error handling has been kept to a minimum to keep the code as clear and concise as possible
 */
class CameraHandler {

    private static final String TAG = "CameraHandler";

    private StreamDataListener streamDataListener;

    public interface StreamDataListener {
        void images(FrameDataHolder dataHolder);

        void images(Bitmap msxBitmap, Bitmap dcBitmap);
    }

    //Discovered FLIR cameras
    LinkedList<Identity> foundCameraIdentities = new LinkedList<>();

    //A FLIR Camera
    private Camera camera;

    private Stream connectedStream;
    private ThermalStreamer streamer;


    public interface DiscoveryStatus {
        void started();

        void stopped();
    }

    public CameraHandler() {
        Log.d(TAG, "CameraHandler constr");
    }

    /**
     * Start discovery of USB and Emulators
     */
    public void startDiscovery(DiscoveryEventListener cameraDiscoveryListener, DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().scan(cameraDiscoveryListener, CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.started();
    }

    /**
     * Stop discovery of USB and Emulators
     */
    public void stopDiscovery(DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().stop(CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.stopped();
    }

    public synchronized void connect(Identity identity, ConnectionStatusListener connectionStatusListener) throws IOException {
        Log.d(TAG, "connect identity: " + identity);
        camera = new Camera();
        camera.connect(identity, connectionStatusListener, new ConnectParameters());
    }

    public synchronized void disconnect() {
        Log.d(TAG, "disconnect");
        if (camera == null) {
            return;
        }
        if (connectedStream == null) {
            return;
        }

        if (connectedStream.isStreaming()) {
            connectedStream.stop();
        }
        camera.disconnect();
        camera = null;
    }

    public synchronized void performNuc() {
        Log.d(TAG, "performNuc");
        if (camera == null) {
            return;
        }
        RemoteControl rc = camera.getRemoteControl();
        if (rc == null) {
            return;
        }
        Calibration calib = rc.getCalibration();
        if (calib == null) {
            return;
        }
        calib.nuc().executeSync();
    }

    /**
     * Start a stream of {@link ThermalImage}s images from a FLIR ONE or emulator
     */
    public synchronized void startStream(StreamDataListener listener) {
        this.streamDataListener = listener;
        if (camera == null || !camera.isConnected()) {
            Log.e(TAG, "startStream, failed, camera was null or not connected");
            return;
        }
        connectedStream = camera.getStreams().get(0);
        if (connectedStream.isThermal()) {
            streamer = new ThermalStreamer(connectedStream);
        } else {
            Log.e(TAG, "startStream, failed, no thermal stream available for the camera");
            return;
        }
        connectedStream.start(
                unused -> {
                    streamer.update();
                    // workaround lambda requiring final object by passing an array of size 1 instead of plain object
                    final Bitmap[] dcBitmap = new Bitmap[1];
                    streamer.withThermalImage(thermalImage -> {
                        dcBitmap[0] = BitmapAndroid.createBitmap(
                                Objects.requireNonNull(
                                        Objects.requireNonNull(
                                                thermalImage.getFusion()).getPhoto())).getBitMap();
                        Log.d(TAG, "adding images to cache");
                    });
                    final Bitmap thermalPixels = BitmapAndroid.createBitmap(streamer.getImage()).getBitMap();
                    streamDataListener.images(thermalPixels, dcBitmap[0]);
                },
                error -> Log.e(TAG, "Streaming error: " + error));
    }

    /**
     * Add a found camera to the list of known cameras
     */
    public void add(Identity identity) {
        foundCameraIdentities.add(identity);
    }

    @Nullable
    public Identity getCppEmulator() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("C++ Emulator")) {
                return foundCameraIdentity;
            }
        }
        return null;
    }

    @Nullable
    public Identity getFlirOneEmulator() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE")) {
                return foundCameraIdentity;
            }
        }
        return null;
    }

    @Nullable
    public Identity getFlirOne() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            boolean isFlirOneEmulator = foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE");
            boolean isCppEmulator = foundCameraIdentity.deviceId.contains("C++ Emulator");
            if (!isFlirOneEmulator && !isCppEmulator) {
                return foundCameraIdentity;
            }
        }

        return null;
    }

    public String getDeviceInfo() {
        if (camera == null) {
            return "N/A";
        }
        RemoteControl rc = camera.getRemoteControl();
        if (rc == null) {
            return "N/A";
        }
        CameraInformation ci = rc.cameraInformation().getSync();
        if (ci == null) {
            return "N/A";
        }
        return ci.displayName + ", SN: " + ci.serialNumber;
    }

}
