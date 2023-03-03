package com.samples.flironecamera;

import android.graphics.Bitmap;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import java.io.*;
import java.net.*;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class SocketHandler {
    private String address;
    private int port;
    private Socket socket;

    private DataOutputStream outputStream;
    private DataInputStream inputStream;
    public Bitmap thermalImg;
    private TextView tempHolder;
    private boolean isBitmapInitialized = false;

    private TemperatureViewModel temperatureViewModel;

    public SocketHandler(String address, int port, TemperatureViewModel model) {
        this.address = address;
        this.port = port;
        this.connect(this.address, this.port);
        this.temperatureViewModel = model;
    }

    public void bitmapInit(Bitmap thermalImg, TextView tempHolder) {
        this.thermalImg = thermalImg;
        this.tempHolder = tempHolder;
        isBitmapInitialized = true;
    }

    public void connect(String address, int port) {
        Log.w("Connect", "Connecting Socket");

        Thread checkUpdate = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket(address, port);
                    Log.w("Socket", "Socket connected");
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    Log.w("Socket", "Socket connection failed - UnknownHost");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.w("Socket", "Socket connection failed - IOException");
                }

                try {
                    outputStream = new DataOutputStream(socket.getOutputStream());
                    inputStream = new DataInputStream(socket.getInputStream());
                    Log.w("Data", "Output/Input stream created");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.w("Data", "Output/Input stream fail - IOException");
                }

                int readByte = 0;

                int count = 0;
                while (true) {
//                    Log.d("bitmapInitialized", String.valueOf(isBitmapInitialized));

                    while (isBitmapInitialized) {
                        count++;
                        if (thermalImg != null) {
                            Log.d("thermal", "img not null");
                            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
                            thermalImg.compress(Bitmap.CompressFormat.JPEG, 100, byteArray);
                            byte[] bitmapBytes = byteArray.toByteArray();
                            Log.w("Data", bitmapBytes.length + " bitmap length");

                            try {
                                outputStream.write(bitmapBytes);
                                outputStream.flush();
                                outputStream.writeBytes("\r\n\r\n");
                                outputStream.flush();
                                Log.w("Data", "image out");
                            } catch (IOException e) {
                                Log.w("Data", "Message send fail - IOException");
                                throw new RuntimeException(e);
                            }

                            byte[] buff = new byte[1024];
                            try {
                                if ((readByte = inputStream.read(buff, 0, 1024)) < 0) break;
//                        readByte = inputStream.read(buff, 0, 1024);
                                Log.w("Data", "message incoming");
                            } catch (IOException e) {
                                Log.w("Data", "Message read fail - IOException");
                                throw new RuntimeException(e);
                            }

                            byte[] temp = new byte[readByte];
                            System.arraycopy(buff, 0, temp, 0, readByte);
                            String tempDetected = new String(temp);

                            // TODO - replace tempHolder text with incoming data - temperature detected
                            Log.w("Received", "Message from server - " + tempDetected);
                            temperatureViewModel.getCurrentTemp().postValue(tempDetected);
                        }
                    }
                }
            }
        });

        checkUpdate.start();
    }


    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

