package org.eclipse.paho.android.sample.activity;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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

import com.opencsv.CSVReader;

import org.eclipse.paho.android.sample.R;
import org.eclipse.paho.android.sample.internal.Connections;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class PublishFragment extends Fragment {

    private Connection connection;

    private int selectedQos = 0;
    private boolean retainValue = false;
    private String topic = "paho/test/simple";
    private String message = "Hello world";
    private Button publishButton;
    private Button publishTimestampButton;
    private Button msgCountButton;
    private Boolean publishTimestampBoolean = false;
    private EditText timeIntervalEditText;
    private int timeInterval;
    private AsyncTask BgTask;

    public PublishFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Map<String, Connection> connections = Connections.getInstance(this.getActivity())
                .getConnections();
        connection = connections.get(this.getArguments().getString(ActivityConstants.CONNECTION_KEY));

        System.out.println("FRAGMENT CONNECTION: " + this.getArguments().getString(ActivityConstants.CONNECTION_KEY));
        System.out.println("NAME:" + connection.getId());

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

    private String getTimeStamp(){

        Long tsLong = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd yyyy HH:mm:ss:SS", Locale.US);
        Date resultDate = new Date(tsLong);
        String ts = sdf.format(resultDate);
        return ts;
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

    private class PublishTimestampTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected Void doInBackground(Void... countArray) {

            int count = 0;
            List<String[]> dataList = readCsv(getContext());

            for(int i = 0; i < dataList.size(); i++) {
                if(isCancelled())
                    break;

                message = dataList.get(i)[0] + "," + dataList.get(i)[1];

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