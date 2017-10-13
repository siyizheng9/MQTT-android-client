package org.eclipse.paho.android.sample.model;

/**
 * Created by zsy on 10/11/17.
 */

public class ecgMessage {
    public String timestamp;
    public String ecg;

    public ecgMessage(String time, String value) {
        this.timestamp = time;
        this.ecg = value;
    }
}
