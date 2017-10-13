package org.eclipse.paho.android.sample.model;

/**
 * Created by zsy on 10/13/17.
 */

public class accMessage {

    public String timestamp;
    public String accelerometer;

    public accMessage(String time, String value) {
        this.timestamp = time;
        this.accelerometer = value;
    }
}
