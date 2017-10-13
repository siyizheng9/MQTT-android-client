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
import android.widget.Toast;

import com.google.gson.Gson;
import com.mbientlab.metawear.AsyncDataProducer;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.AngularVelocity;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.AccelerometerBosch;
import com.mbientlab.metawear.module.AccelerometerMma8452q;
import com.mbientlab.metawear.module.GyroBmi160;
import com.opencsv.CSVReader;

import org.eclipse.paho.android.sample.R;
import org.eclipse.paho.android.sample.internal.Connections;
import org.eclipse.paho.android.sample.model.accMessage;
import org.eclipse.paho.android.sample.model.ecgMessage;
import org.eclipse.paho.android.sample.model.gyroMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import bolts.Continuation;
import bolts.Task;


public class PublishFragment extends Fragment implements ServiceConnection {

    private final String LOGTAG = "PublishFragment";

    // accelerator parameter
    private static final float[] MMA845Q_RANGES= {2.f, 4.f, 8.f}, BOSCH_RANGES = {2.f, 4.f, 8.f, 16.f};
    private static final float ACC_FREQ= 50.f;
    protected float samplePeriod;
    protected Route streamRoute = null;

    // gyro parametter
    private static final float[] AVAILABLE_RANGES= {125.f, 250.f, 500.f, 1000.f, 2000.f};
    private static final float GYR_ODR= 25.f;
    private GyroBmi160 gyro = null;

    private Connection connection;
    private BtleService.LocalBinder serviceBinder;
    private final String MW_MAC_ADDRESS= "CC:7E:26:31:C2:5F";
    private MetaWearBoard board;
    private Accelerometer accelerometer = null;
    private int rangeIndex= 0;

    private BlockingQueue mqtt_queue;
    Gson gson = new Gson();

    private int selectedQos = 0;
    private boolean retainValue = false;
    private boolean boardReady = false;
    private Boolean isPublish = false;

    // private String topic = "paho/test/simple";
    private String topic = "ecg/test/client-id/data";
    private String message = "Hello world";
    private Button publishButton;
    private Button msgCountButton;
    private Button sensorConnectButton;
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
                System.out.println("Publishing timestamp: [topic: " + topic + ", QoS: " + selectedQos + ", Retain: " + retainValue + "]");
                if(isPublish == false){
                    startPublish();
                }
                else{
                    stopPublish();
                }
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
                                Log.i(LOGTAG, "Failed to connect MotionSensor");
                            } else {
                                Log.i(LOGTAG, "MotionSensor Connected");
                                isSensorConnected = true;
                            }
                            getActivity().runOnUiThread(new Runnable() {
                                public void run() {
                                    if (isSensorConnected) {

                                        sensorConnectButton.setText("Disconnect");
                                        try {
                                            boardReady = true;
                                            boardReady();
                                        } catch (UnsupportedModuleException e) {
                                            Log.i(LOGTAG, "unsupportedModule");
                                        }
                                    }
                                    else
                                        Notify.toast(getContext(), "Connection failed", Toast.LENGTH_SHORT);

                                    sensorConnectButton.setEnabled(true);
                                }
                            });
                            return null;
                        }
                    });
                } else {
                    board.disconnectAsync().continueWith(new Continuation<Void, Void>() {
                        @Override
                        public Void then(Task<Void> task) throws Exception {
                            Log.i(LOGTAG, "MotionSensor Disconnected");
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
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mqtt_queue = new LinkedBlockingQueue();

        ((Switch) view.findViewById(R.id.acc_control)).setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                setup_acc();
            } else {
                acc_clean();
                if (streamRoute != null) {
                    streamRoute.remove();
                    streamRoute = null;
                }
            }
        });

        ((Switch) view.findViewById(R.id.gyro_control)).setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                gyro_setup();
            } else {
                gyro_clean();
                if (streamRoute != null) {
                    streamRoute.remove();
                    streamRoute = null;
                }
            }
        });

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Unbind the service when the activity is destroyed
        getActivity().getApplicationContext().unbindService(this);
    }

    private void startPublish(){
        isPublish = true;
        publishButton.setText(getResources().getString(R.string.stop_publish));
        msgCountButton.setVisibility(View.VISIBLE);
        new Notify().EnableToast = false;
        BgTask = new PublishTimestampTask().execute();

    }

    private void stopPublish(){
        isPublish = false;
        BgTask.cancel(true);
        publishButton.setText(getResources().getString(R.string.start_publish));
        new Notify().EnableToast = true;

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
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd yyyy HH:mm:ss:SSS", Locale.US);
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
        board = serviceBinder.getMetaWearBoard(remoteDevice);
    }

    private void setup_acc() {
        Accelerometer.ConfigEditor<?> editor = accelerometer.configure();

        editor.odr(ACC_FREQ);
        if (accelerometer instanceof AccelerometerBosch) {
            editor.range(BOSCH_RANGES[rangeIndex]);
        } else if (accelerometer instanceof AccelerometerMma8452q) {
            editor.range(MMA845Q_RANGES[rangeIndex]);
        }
        editor.commit();

        samplePeriod = 1 / accelerometer.getOdr();

        final AsyncDataProducer producer = accelerometer.packedAcceleration() == null ?
                accelerometer.packedAcceleration() :
                accelerometer.acceleration();
        producer.addRouteAsync(source -> source.stream((data, env) -> {
            final Acceleration value = data.value(Acceleration.class);
            //Log.i(LOGTAG, "acc value: " + value.toString());
            accMessage acc = new accMessage(getTimeStamp(), value.toString());
            mqtt_queue.add(gson.toJson(acc));
            //addChartData(value.x(), value.y(), value.z(), samplePeriod);
        })).continueWith(task -> {
            streamRoute = task.getResult();
            producer.start();
            accelerometer.start();
            return null;
        });
    }

    private void acc_clean() {
        accelerometer.stop();

        (accelerometer.packedAcceleration() == null ?
                accelerometer.packedAcceleration() :
                accelerometer.acceleration()
        ).stop();
    }

    protected void gyro_setup() {
        GyroBmi160.Range[] values = GyroBmi160.Range.values();
        gyro.configure()
                .odr(GyroBmi160.OutputDataRate.ODR_25_HZ)
                .range(values[values.length - rangeIndex - 1])
                .commit();

        final float period = 1 / GYR_ODR;
        final AsyncDataProducer producer = gyro.packedAngularVelocity() == null ?
                gyro.packedAngularVelocity() :
                gyro.angularVelocity();
        producer.addRouteAsync(source -> source.stream((data, env) -> {
            final AngularVelocity value = data.value(AngularVelocity.class);
            gyroMessage gyro = new gyroMessage(getTimeStamp(), value.toString());
            mqtt_queue.add(gson.toJson(gyro));
        })).continueWith(task -> {
            streamRoute = task.getResult();

            gyro.angularVelocity().start();
            gyro.start();

            return null;
        });
    }

    protected void gyro_clean() {
        gyro.stop();

        (gyro.packedAngularVelocity() == null ?
                gyro.packedAngularVelocity() :
                gyro.angularVelocity()
        ).stop();
    }

    private void boardReady() throws UnsupportedModuleException {
        accelerometer = board.getModuleOrThrow(Accelerometer.class);
        gyro = board.getModuleOrThrow(GyroBmi160.class);

    }


    private class PublishTimestampTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected Void doInBackground(Void... countArray) {

            int count = 0;

            for(; ;) {
                if(isCancelled())
                    break;

                String value = null;
                try{

                    value = (String) mqtt_queue.take();
                } catch (InterruptedException ex) {

                }

                if (value == null)
                    continue;

                message = value;

                ((MainActivity) getActivity()).publish(connection, topic, message, selectedQos, retainValue);

                count++;
                publishProgress(count);
                // SystemClock.sleep(timeInterval);

            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            msgCountButton.setText("message count: " + progress[0]);
        }
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