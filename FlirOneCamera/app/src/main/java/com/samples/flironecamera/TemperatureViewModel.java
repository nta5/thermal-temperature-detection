package com.samples.flironecamera;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class TemperatureViewModel extends ViewModel {
    private static final TemperatureViewModel instance = new TemperatureViewModel();

    // Create a LiveData with a String
    private MutableLiveData<String> currentTemp;
    private MutableLiveData<byte[]> imageByte;

    private TemperatureViewModel() {
    }

    public static TemperatureViewModel getInstance() {
        return instance;
    }

    public MutableLiveData<String> getCurrentTemp() {
        if (currentTemp == null) {
            currentTemp = new MutableLiveData<String>();
        }
        return currentTemp;
    }

    public MutableLiveData<byte[]> getImageByte() {
        if (imageByte == null) {
            imageByte = new MutableLiveData<byte[]>();
        }
        return imageByte;
    }
}
