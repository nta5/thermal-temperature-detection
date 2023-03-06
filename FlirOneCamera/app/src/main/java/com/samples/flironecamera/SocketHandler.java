package com.samples.flironecamera;

import android.graphics.Bitmap;
import android.util.Log;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

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

    public boolean isConnected() {
        return socket != null && socket.isConnected();
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

                while (true) {
                    try {
//                    Log.d("bitmapInitialized", String.valueOf(isBitmapInitialized));
                        while (isBitmapInitialized) {
                            if (thermalImg != null) {
                                Log.d("thermal", "img not null");
                                ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
                                thermalImg.compress(Bitmap.CompressFormat.JPEG, 100, byteArray);
                                byte[] bitmapBytes = byteArray.toByteArray();
//                            Log.w("Data", bitmapBytes.length + " bitmap length");

                                try {
                                    outputStream.write(bitmapBytes);
                                    outputStream.flush();
                                    outputStream.writeBytes("\r\n\r\n\r\n");
                                    outputStream.flush();
                                    Log.w("Data", "image out");
                                } catch (IOException e) {
                                    Log.w("Data", "Message send fail - IOException");
                                    throw new RuntimeException(e);
                                }

                                byte[] buff = new byte[1024];
                                try {
                                    if ((readByte = inputStream.read(buff, 0, 1024)) < 0) break;
                                    Log.w("Data", "message incoming");
                                } catch (IOException e) {
                                    Log.w("Data", "Message read fail - IOException");
                                    throw new RuntimeException(e);
                                }

                                byte[] temp = new byte[readByte];
                                System.arraycopy(buff, 0, temp, 0, readByte);
                                String tempDetected = new String(temp);

                                Log.w("Received", "Message from server - " + tempDetected);
                                temperatureViewModel.getCurrentTemp().postValue(tempDetected);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        break;
                    }
                }
            }
        });

        checkUpdate.start();
    }

    public void capture() {
        Thread checkUpdate = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket captureSocket = null;
                DataOutputStream captureOutput = null;
                DataInputStream captureInput = null;
                try {
                    captureSocket = new Socket(address, port);
                    Log.w("DEBUG", "Socket connected");
                    captureOutput = new DataOutputStream(captureSocket.getOutputStream());
                    captureInput = new DataInputStream(captureSocket.getInputStream());
                    Log.w("DEBUG", "Output/Input stream created");
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    Log.w("DEBUG", "Socket connection failed - UnknownHost");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.w("DEBUG", "Socket connection failed - IOException");
                }

                int readByte = 0;
                try {
                    while (true) {
//                    Log.d("bitmapInitialized", String.valueOf(isBitmapInitialized));
                        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
                        thermalImg.compress(Bitmap.CompressFormat.JPEG, 100, byteArray);
                        byte[] bitmapBytes = byteArray.toByteArray();

                        try {
                            captureOutput.writeBytes("Capture");
                            captureOutput.write(bitmapBytes);
                            captureOutput.flush();
                            captureOutput.writeBytes("\r\n\r\n\r\n");
                            captureOutput.flush();
                            Log.w("DEBUG", "capture out");
                        } catch (IOException e) {
                            Log.w("DEBUG", "Message send fail - IOException");
                        }

                        byte[] buff = new byte[400000];
                        int off = 0;
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        try {
                            while ((readByte = captureInput.read(buff)) != -1) {
                                out.write(buff, 0, readByte);
                                off += readByte;
                                Log.w("DEBUG", "Read length: " + off);
                                String s = new String(buff, StandardCharsets.UTF_8);
                                if (s.contains("\r\n\r\n\r\n")) break;
                            }
                        } catch (IOException e) {
                            Log.w("DEBUG", "Message read fail - IOException: " + e.getMessage());
                        }

                        byte[] imageBytes = out.toByteArray();
                        Log.d("DEBUG", "Written");
                        temperatureViewModel.getImageByte().postValue(imageBytes);
                        captureSocket.close();
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
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

