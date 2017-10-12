package org.eclipse.paho.android.sample.activity;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.google.gson.Gson;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.android.BtleService;
import com.opencsv.CSVReader;

import org.eclipse.paho.android.sample.R;
import org.eclipse.paho.android.sample.internal.Connections;
import org.eclipse.paho.android.sample.model.ecgMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import bolts.Continuation;
import bolts.Task;


public class PublishFragment extends Fragment implements ServiceConnection {

    private Connection connection;
    private BtleService.LocalBinder serviceBinder;
    private final String MW_MAC_ADDRESS= "CC:7E:26:31:C2:5F";
    private MetaWearBoard board;

    private int selectedQos = 0;
    private boolean retainValue = false;
    // private String topic = "paho/test/simple";
    private String topic = "ecg/test/client-id/data";
    private String message = "Hello world";
    private Button publishButton;
    private Button publishTimestampButton;
    private Button msgCountButton;
    private Button sensorConnectButton;
    private Boolean publishTimestampBoolean = false;
    private Boolean isSensorConnected = false;
    private EditText timeIntervalEditText;
    private TextView motionSensorLable;
    private int timeInterval;
    private AsyncTask BgTask;

    public PublishFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        String android_id = Settings.Secure.getString(getContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
        System.out.println("android_id: " + android_id);
        topic = topic.replace("id", android_id.substring(0, 4));

        super.onCreate(savedInstanceState);
        Map<String, Connection> connections = Connections.getInstance(this.getActivity())
                .getConnections();
        connection = connections.get(this.getArguments().getString(ActivityConstants.CONNECTION_KEY));

        System.out.println("FRAGMENT CONNECTION: " + this.getArguments().getString(ActivityConstants.CONNECTION_KEY));
        System.out.println("NAME:" + connection.getId());

        // Bind the service when the activity is created
        getActivity().getApplicationContext().bindService(new Intent(getActivity(), BtleService.class),
                this, Context.BIND_AUTO_CREATE);



    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_publish, container, false);
        EditText topicText = (EditText) rootView.findViewById(R.id.topic);
        EditText messageText = (EditText) rootView.findViewById(R.id.message);
        Spinner qos = (Spinner) rootView.findViewById(R.id.qos_spinner);
        final Switch retain = (Switch) rootView.findViewById(R.id.retain_switch);
        topicText.setText(topic);

        topicText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                topic = s.toString();
            }
        });

        messageText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                message = s.toString();
            }
        });

        qos.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedQos = Integer.parseInt(getResources().getStringArray(R.array.qos_options)[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        retain.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                retainValue = isChecked;
            }
        });

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getActivity(), R.array.qos_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qos.setAdapter(adapter);

        publishButton = (Button) rootView.findViewById(R.id.publish_button);
        publishButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println("Publishing: [topic: " + topic + ", message: " + message + ", QoS: " + selectedQos + ", Retain: " + retainValue + "]");
                ((MainActivity) getActivity()).publish(connection, topic, message, selectedQos, retainValue);


            }
        });

        msgCountButton = (Button) rootView.findViewById(R.id.msg_count_button);
        motionSensorLable = (TextView) rootView.findViewById(R.id.motion_sensor_label);
        motionSensorLable.setText(Html.fromHtml("Motion sensor <br> MAC:" + MW_MAC_ADDRESS));
        sensorConnectButton = (Button) rootView.findViewById(R.id.connectButton);
        sensorConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sensorConnectButton.setEnabled(false);
                if(isSensorConnected == false) {
                    board.connectAsync().continueWith(new Continuation<Void, Void>() {
                        @Override
                        public Void then(Task<Void> task) throws Exception {
                            if (task.isFaulted()) {
                                Log.i("MainActivity", "Failed to connect MotionSensor");
                            } else {
                                Log.i("MainActivity", "MotionSensor Connected");
                                isSensorConnected = true;

                                getActivity().runOnUiThread(new Runnable() {
                                    public void run() {
                                        sensorConnectButton.setText("Disconnect");
                                        sensorConnectButton.setEnabled(true);
                                    }
                                });
                            }
                            return null;
                        }
                    });
                } else {
                    board.disconnectAsync().continueWith(new Continuation<Void, Void>() {
                        @Override
                        public Void then(Task<Void> task) throws Exception {
                            Log.i("MainActivity", "MotionSensor Disconnected");
                            isSensorConnected = false;
                            getActivity().runOnUiThread(new Runnable() {
                                public void run() {
                                    sensorConnectButton.setText("Connect");
                                    sensorConnectButton.setEnabled(true);
                                }
                            });
                            return null;
                        }
                    });
                }

            }
        });

        publishTimestampButton = (Button) rootView.findViewById(R.id.publish_timestamp_button);
        publishTimestampButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println("Publishing timestamp: [topic: " + topic + ", QoS: " + selectedQos + ", Retain: " + retainValue + "]");
                if(publishTimestampBoolean == false){
                    startPublishTimestamp();
                }
                else{
                    stopPublishTimestamp();
                }
            }
        });

        timeIntervalEditText = (EditText) rootView.findViewById(R.id.time_interval);
        timeInterval = Integer.valueOf(timeIntervalEditText.getText().toString());
        System.out.println("default time interval: " + timeInterval);

        timeIntervalEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String str = s.toString();
                if (TextUtils.isEmpty(str)) {
                    str = "0";
                }
                timeInterval = Integer.valueOf(str);
                System.out.println("new time interval: " + timeInterval);
            }
        });

        // Inflate the layout for this fragment
        return rootView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Unbind the service when the activity is destroyed
        getActivity().getApplicationContext().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // Typecast the binder to the service's LocalBinder class
        serviceBinder = (BtleService.LocalBinder) service;
        retrieveBoard();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) { }


    private String getTimeStamp(){

        Long tsLong = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd yyyy HH:mm:ss:SS", Locale.US);
        Date resultDate = new Date(tsLong);
        String ts = sdf.format(resultDate);
        return ts;
    }

    public void retrieveBoard() {
        final BluetoothManager btManager=
                (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice=
                btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS);

        // Create a MetaWear board object for the Bluetooth Device
        board= serviceBinder.getMetaWearBoard(remoteDevice);
    }



    private class PublishTimestampTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected Void doInBackground(Void... countArray) {

            int count = 0;
            List<String[]> dataList = readCsv(getContext());
            Gson gson = new Gson();

            for(int i = 0; i < dataList.size(); i++) {
                if(isCancelled())
                    break;

                ecgMessage record = new ecgMessage(dataList.get(i)[0], dataList.get(i)[1]);
                message = gson.toJson(record);

                ((MainActivity) getActivity()).publish(connection, topic, message, selectedQos, retainValue);

                count++;
                publishProgress(count);
                SystemClock.sleep(timeInterval);

            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            msgCountButton.setText("message count: " + progress[0]);
        }
    }


    private void startPublishTimestamp(){
        publishTimestampBoolean = true;
        publishButton.setEnabled(false);
        publishTimestampButton.setText(getResources().getString(R.string.stop_publish_timestamp));
        msgCountButton.setVisibility(View.VISIBLE);
        new Notify().EnableToast = false;
        BgTask = new PublishTimestampTask().execute();

    }

    private void stopPublishTimestamp(){
        publishTimestampBoolean = false;
        BgTask.cancel(true);
        publishButton.setEnabled(true);
        publishTimestampButton.setText(getResources().getString(R.string.publish_timestamp));
        new Notify().EnableToast = true;

    }

    private final List<String[]> readCsv(Context context) {
        List<String[]> dataList = new ArrayList<String[]>();
        AssetManager assetManager = context.getAssets();

        try {
            InputStream csvStream = assetManager.open("sample_data.csv");
            InputStreamReader csvStreamReader = new InputStreamReader(csvStream);
            CSVReader csvReader = new CSVReader(csvStreamReader);
            String[] line;

            while ((line = csvReader.readNext()) != null) {
                dataList.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (dataList.size() > 0) {
            System.out.println("ecg data: " + dataList.get(0)[0] + "," + dataList.get(0)[1]);
        }
        return dataList;
    }

}