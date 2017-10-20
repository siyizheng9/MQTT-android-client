package org.eclipse.paho.android.sample.model;

/**
 * Created by zsy on 10/13/17.
 */

public class magnetMessage {

    public String timestamp;
    public String type = "magnetometer";
    public float x;
    public float y;
    public float z;

    public magnetMessage(String time, float x, float y, float z) {
        this.timestamp = time;
        this.x = x;
        this.y = y;
        this.z = z;
    }

}
