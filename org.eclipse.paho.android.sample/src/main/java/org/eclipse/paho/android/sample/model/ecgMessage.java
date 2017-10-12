package org.eclipse.paho.android.sample.model;

/**
 * Created by zsy on 10/11/17.
 */

public class ecgRecord {
    public String date;
    public String value;

    public ecgRecord(String date, String value) {
        this.date = date;
        this.value = value;
    }
}
