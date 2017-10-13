package org.eclipse.paho.android.sample.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by zsy on 10/13/17.
 */

public class mqttMessage {
    String[] message;

    public mqttMessage(String[] dataList) {
        this.message = dataList;
    }
}
