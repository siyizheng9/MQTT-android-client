package org.eclipse.paho.android.sample.model;

/**
 * Created by zsy on 10/13/17.
 */

public class gyroMessage {

    public String timestamp;
    public String type = "gyro";

    public float x;
    public float y;
    public float z;

    public gyroMessage(String time, float x, float y, float z) {
        this.timestamp = time;
        this.x = x;
        this.y = y;
        this.z = z;
    }

}
