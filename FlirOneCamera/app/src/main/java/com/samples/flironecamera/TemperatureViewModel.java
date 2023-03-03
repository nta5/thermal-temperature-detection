package com.samples.flironecamera;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class TemperatureViewModel extends ViewModel {

    // Create a LiveData with a String
    private MutableLiveData<String> currentTemp;

    public MutableLiveData<String> getCurrentTemp() {
        if (currentTemp == null) {
            currentTemp = new MutableLiveData<String>();
        }
        return currentTemp;
    }
}
