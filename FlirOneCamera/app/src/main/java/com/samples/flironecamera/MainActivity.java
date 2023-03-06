/*
 * ******************************************************************
 * @title FLIR THERMAL SDK
 * @file MainActivity.java
 * @Author FLIR Systems AB
 *
 * @brief  Main UI of test application
 *
 * Copyright 2019:    FLIR Systems
 * ******************************************************************/
package com.samples.flironecamera;

import static android.os.StrictMode.setThreadPolicy;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveredCamera;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.log.ThermalLog;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Sample application for scanning a FLIR ONE or a built in emulator
 * <p>
 * See the {@link CameraHandler} for how to preform discovery of a FLIR ONE camera, connecting to it and start streaming images
 * <p>
 * The MainActivity is primarily focused to "glue" different helper classes together and updating the UI components
 * <p/>
 * Please note, this is <b>NOT</b> production quality code, error handling has been kept to a minimum to keep the code as clear and concise as possible
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    //Handles Android permission for eg Network
    private PermissionHandler permissionHandler;

    //Handles network camera operations
    private CameraHandler cameraHandler;

    private SocketHandler socketHandler;
    private TemperatureViewModel temperatureViewModel;

    private Identity connectedIdentity = null;
    private TextView connectionStatus;
    private TextView discoveryStatus;
    private TextView deviceInfo;
    private TextView tempHolder;

    private ImageView msxImage;
    private ImageView photoImage;

    private final LinkedBlockingQueue<FrameDataHolder> framesBuffer = new LinkedBlockingQueue<>(21);
    private final UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();

    /**
     * Show message on the screen
     */
    public interface ShowMessage {
        void show(String message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        setThreadPolicy(policy);

        ThermalLog.LogLevel enableLoggingInDebug = ThermalLog.LogLevel.NONE;

        //ThermalSdkAndroid has to be initiated from a Activity with the Application Context to prevent leaking Context,
        // and before ANY using any ThermalSdkAndroid functions
        //ThermalLog will show log from the Thermal SDK in standards android log framework
        ThermalSdkAndroid.init(getApplicationContext(), enableLoggingInDebug);

        permissionHandler = new PermissionHandler(showMessage, MainActivity.this);

        cameraHandler = new CameraHandler();

        setupViews();

        // Set up view model for live data
        temperatureViewModel = TemperatureViewModel.getInstance();
        final Observer<String> tempObserver = newTemp -> tempHolder.setText(getString(R.string.temperature_text, newTemp));
        temperatureViewModel.getCurrentTemp().observe(this, tempObserver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Always close the connection with a connected FLIR ONE when going into background
        disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        socketHandler.close();
    }

    public void startDiscovery(View view) {
        startDiscovery();
        startSocketConnection();
    }

    public void stopDiscovery(View view) {
        stopDiscovery();
    }

    public void connectFlirOne(View view) {
        connect(cameraHandler.getFlirOne());
    }

    public void connectSimulatorOne(View view) {
        connect(cameraHandler.getCppEmulator());
    }

    public void connectSimulatorTwo(View view) {
        connect(cameraHandler.getFlirOneEmulator());
    }

    public void disconnect(View view) {
        disconnect();
    }

    public void performNuc(View view) {
        cameraHandler.performNuc();
    }

    public void captureImage(View view) {
        captureImage();
    }

    /**
     * Handle Android permission request response for Bluetooth permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult() called with: requestCode = [" + requestCode + "], permissions = ["
                + Arrays.toString(permissions) + "], grantResults = [" + Arrays.toString(grantResults) + "]");
        permissionHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Connect to a Camera
     */
    private void connect(Identity identity) {
        //We don't have to stop a discovery but it's nice to do if we have found the camera that we are looking for
        cameraHandler.stopDiscovery(discoveryStatusListener);

        if (connectedIdentity != null) {
            Log.d(TAG, "connect(), in *this* code sample we only support one camera connection at the time");
            showMessage.show("connect(), in *this* code sample we only support one camera connection at the time");
            return;
        }

        if (identity == null) {
            Log.d(TAG, "connect(), can't connect, no camera available");
            showMessage.show("connect(), can't connect, no camera available");
            return;
        }

        connectedIdentity = identity;

        updateConnectionText(identity, "CONNECTING");
        //IF your using "USB_DEVICE_ATTACHED" and "usb-device vendor-id" in the Android Manifest
        // you don't need to request permission, see documentation for more information
        if (UsbPermissionHandler.isFlirOne(identity)) {
            usbPermissionHandler.requestFlirOnePermisson(identity, this, permissionListener);
        } else {
            doConnect(identity);
        }

    }

    private final UsbPermissionHandler.UsbPermissionListener permissionListener = new UsbPermissionHandler.UsbPermissionListener() {
        @Override
        public void permissionGranted(@NonNull Identity identity) {
            doConnect(identity);
        }

        @Override
        public void permissionDenied(@NonNull Identity identity) {
            MainActivity.this.showMessage.show("Permission was denied for identity ");
        }

        @Override
        public void error(UsbPermissionHandler.UsbPermissionListener.ErrorType errorType, final Identity identity) {
            MainActivity.this.showMessage.show("Error when asking for permission for FLIR ONE, error:" + errorType + " identity:" + identity);
        }
    };

    private void doConnect(Identity identity) {
        new Thread(() -> {
            try {
                cameraHandler.connect(identity, connectionStatusListener);
                runOnUiThread(() -> {
                    updateConnectionText(identity, "CONNECTED");
                    deviceInfo.setText(cameraHandler.getDeviceInfo());
                });
                cameraHandler.startStream(streamDataListener);
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Could not connect: " + e);
                    updateConnectionText(identity, "DISCONNECTED");
                });
            }
        }).start();
    }

    /**
     * Disconnect to a camera
     */
    private void disconnect() {
        updateConnectionText(connectedIdentity, "DISCONNECTING");
        Log.d(TAG, "disconnect() called with: connectedIdentity = [" + connectedIdentity + "]");
        connectedIdentity = null;
        new Thread(() -> {
            cameraHandler.disconnect();
            if (socketHandler != null) socketHandler.close();
            runOnUiThread(() -> updateConnectionText(null, "DISCONNECTED"));
        }).start();
    }

    /**
     * Update the UI text for connection status
     */
    private void updateConnectionText(Identity identity, String status) {
        String deviceId = identity != null ? identity.deviceId : "";
        connectionStatus.setText(getString(R.string.connection_status_text, deviceId + " " + status));
    }

    /**
     * Start camera discovery
     */
    private void startDiscovery() {
        cameraHandler.startDiscovery(cameraDiscoveryListener, discoveryStatusListener);
    }

    /**
     * Stop camera discovery
     */
    private void stopDiscovery() {
        cameraHandler.stopDiscovery(discoveryStatusListener);
    }

    /**
     * Callback for discovery status, using it to update UI
     */
    private final CameraHandler.DiscoveryStatus discoveryStatusListener = new CameraHandler.DiscoveryStatus() {
        @Override
        public void started() {
            discoveryStatus.setText(getString(R.string.connection_status_text, "discovering"));
        }

        @Override
        public void stopped() {
            discoveryStatus.setText(getString(R.string.connection_status_text, "not discovering"));
        }
    };

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private final ConnectionStatusListener connectionStatusListener = errorCode -> {
        Log.d(TAG, "onDisconnected errorCode:" + errorCode);

        runOnUiThread(() -> updateConnectionText(connectedIdentity, "DISCONNECTED"));
    };

    private final CameraHandler.StreamDataListener streamDataListener = new CameraHandler.StreamDataListener() {

        @Override
        public void images(FrameDataHolder dataHolder) {

            runOnUiThread(() -> {
                msxImage.setImageBitmap(dataHolder.msxBitmap);
                photoImage.setImageBitmap(dataHolder.dcBitmap);
            });
        }

        @Override
        public void images(Bitmap msxBitmap, Bitmap dcBitmap) {

            try {
                framesBuffer.put(new FrameDataHolder(msxBitmap, dcBitmap, socketHandler, tempHolder));
            } catch (InterruptedException e) {
                //if interrupted while waiting for adding a new item in the queue
                Log.e(TAG, "images(), unable to add incoming images to frames buffer, exception:" + e);
            }

            runOnUiThread(() -> {
                Log.d(TAG, "framebuffer size:" + framesBuffer.size());
                FrameDataHolder poll = framesBuffer.poll();
                msxImage.setImageBitmap(Objects.requireNonNull(poll).msxBitmap);
                photoImage.setImageBitmap(Objects.requireNonNull(poll).dcBitmap);
            });

        }
    };

    /**
     * Camera Discovery thermalImageStreamListener, is notified if a new camera was found during a active discovery phase
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private final DiscoveryEventListener cameraDiscoveryListener = new DiscoveryEventListener() {
        @Override
        public void onCameraFound(DiscoveredCamera discoveredCamera) {
            Log.d(TAG, "onCameraFound identity:" + discoveredCamera.getIdentity());
            runOnUiThread(() -> cameraHandler.add(discoveredCamera.getIdentity()));
        }

        @Override
        public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode) {
            Log.d(TAG, "onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);

            runOnUiThread(() -> {
                stopDiscovery();
                MainActivity.this.showMessage.show("onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
            });
        }
    };

    private final ShowMessage showMessage = message -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();

    private void startSocketConnection() {
        if (socketHandler != null && socketHandler.isConnected()) socketHandler.close();
        try {
            socketHandler = new SocketHandler("192.168.1.71", 8800, temperatureViewModel);
        } catch (Exception e) {
            MainActivity.this.showMessage.show("Failed to establish a connection with server.");
        }
    }

    private void captureImage() {
        boolean success = FrameDataHolder.capture();
        if (success) {
            Intent intent = new Intent(this, CaptureImageActivity.class);
            startActivity(intent);
        } else {
            MainActivity.this.showMessage.show("Failed to capture image.");
        }
    }

    private void setupViews() {
        connectionStatus = findViewById(R.id.connection_status_text);
        discoveryStatus = findViewById(R.id.discovery_status);
        deviceInfo = findViewById(R.id.device_info_text);
        tempHolder = findViewById(R.id.temperature);
        msxImage = findViewById(R.id.msx_image);
        photoImage = findViewById(R.id.photo_image);
    }

}
