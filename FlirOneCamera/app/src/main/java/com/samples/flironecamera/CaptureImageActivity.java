package com.samples.flironecamera;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;

public class CaptureImageActivity extends AppCompatActivity {
    private TemperatureViewModel temperatureViewModel;
    private ImageView capturedImage;
    private Observer<byte[]> imageObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_image);

        capturedImage = findViewById(R.id.captured_image);

        temperatureViewModel = TemperatureViewModel.getInstance();
        imageObserver = newImageBytes -> {
            try {
                Bitmap bmp = BitmapFactory.decodeByteArray(newImageBytes, 0, newImageBytes.length);
                if (capturedImage != null)
                    capturedImage.setImageBitmap(Bitmap.createBitmap(bmp));
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        temperatureViewModel.getImageByte().observe(this, imageObserver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        temperatureViewModel.getImageByte().removeObserver(imageObserver);
    }
}