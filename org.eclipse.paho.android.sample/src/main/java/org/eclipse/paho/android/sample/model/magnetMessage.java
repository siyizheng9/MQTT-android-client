package org.eclipse.paho.android.sample.model;

/**
 * Created by zsy on 10/13/17.
 */

public class magnetMessage {

    public String timestamp;
    public String magnetometer;

    public magnetMessage(String time, String value) {
        this.timestamp = time;
        this.magnetometer = value;
    }
}
