package org.eclipse.paho.android.sample.model;

/**
 * Created by zsy on 10/13/17.
 */

public class gyroMessage {

    public String timestamp;
    public String gyro;

    public gyroMessage(String timestamp, String value) {
        this.timestamp = timestamp;
        this.gyro = value;
    }
}
