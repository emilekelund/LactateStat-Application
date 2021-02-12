package com.example.lactatestat.activities;

import android.Manifest;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.lactatestat.R;
import com.example.lactatestat.services.BleService;
import com.example.lactatestat.services.GattActions;
import com.example.lactatestat.utilities.MessageUtils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Objects;

import static com.example.lactatestat.services.GattActions.ACTION_GATT_LACTATESTAT_EVENT;
import static com.example.lactatestat.services.GattActions.EVENT;
import static com.example.lactatestat.services.GattActions.LACTATESTAT_DATA;
import static com.example.lactatestat.utilities.MessageUtils.createDialog;

public class SessionActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    private static final String TAG = SessionActivity.class.getSimpleName();
    private static final String SELECTED_DEVICE = "selectedDevice";
    private static final String BIAS_VOLTAGE_INDEX = "biasVoltageIndex";
    private static final String BIAS_POLARITY_INDEX = "biasPolarityIndex";
    private static final String TIA_GAIN_INDEX = "tiaGainIndex";
    private static final String LOAD_RESISTOR_INDEX = "loadResistorIndex";
    private static final String INTERNAL_ZERO_INDEX = "internalZeroIndex";

    private BluetoothDevice mSelectedDevice = null;
    private BleService mBluetoothLeService;
    private String mDeviceAddress;

    private TextView mCurrentView;
    private TextView mVoltageView;
    private TextView mConnectionStatusView;
    private ImageView mStatusIcon;
    private TextView mLeftAxisLabel;

    private SwitchCompat mSaveDataSwitch;
    private ToggleButton mPauseSaveButton;

    private static final DateFormat df = new SimpleDateFormat("yyMMdd_HH:mm"); // Custom date format for file saving
    private FileOutputStream dataSample = null;

    private Spinner mBiasVoltageSpinner;
    private Spinner mBiasPolaritySpinner;

    private int mBiasVoltageIndex;
    private int mBiasPolarityIndex;
    private int mTiaGainIndex;
    private int mLoadResistorIndex;
    private int mInternalZeroIndex;

    private boolean firstStart = true;

    private int mTiacnRegister = 31;
    private int mRefcnRegister = 2;

    private static final ArrayList<Integer> mTiaGainValues = new ArrayList<>(
            Arrays.asList(0, 2750, 3500, 7000, 14000, 35000, 120000, 350000)
    );
    private static final ArrayList<Double> mInternalZeroValues = new ArrayList<>(
            Arrays.asList(0.2, 0.5, 0.67)
    );

    private ILineDataSet set = null;
    private LineChart mChart;
    private Thread mThread;
    private static boolean plotData = true;

    private long timeSinceSamplingStart = 0;

    private final CountDownTimer mSamplingTimeTimer = new
            CountDownTimer(86400000, 50) {
                @Override
                public void onTick(long millisUntilFinished) {
                    timeSinceSamplingStart = 86400000 - millisUntilFinished;
                }

                @Override
                public void onFinish() {

                }
            };

    private final CountDownTimer mRegisterUpdateTimer = new
            CountDownTimer(3700, 3000) {
                @Override
                public void onTick(long l) {
                    sendRegisterUpdate();
                }

                @Override
                public void onFinish() {
                    firstStart = false;
                    Log.i(TAG, "Timer Finished");
                }
            }.start();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        // TOOLBAR SETUP
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("LactateStat Session");
        // TOOLBAR: BACK ARROW
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        myToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        mPauseSaveButton = findViewById(R.id.pause_save_button);
        mSaveDataSwitch =  findViewById(R.id.save_data_switch);

        mSaveDataSwitch.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            if (isChecked) {
                // Button is checked, create a new file and start the timer
                dataSample = createFiles();
                mSamplingTimeTimer.start();
                MessageUtils.showToast("Data saving started", getApplicationContext());
            } else {
                try {
                    // Button is unchecked, close the file
                    closeFiles(dataSample);
                    MessageUtils.showToast("Data is now stored on your phone.", getApplicationContext());
                    mSamplingTimeTimer.cancel();
                    timeSinceSamplingStart = 0;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mPauseSaveButton.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            if (isChecked) {
                // Button is checked, change to play icon
                Drawable playIcon = ContextCompat.getDrawable(this, R.drawable.ic_baseline_play_arrow_24);
                mPauseSaveButton.setCompoundDrawablesWithIntrinsicBounds(playIcon,null,null,null);
            } else {
                // Button is unchecked, change icon back
                Drawable pauseIcon = ContextCompat.getDrawable(this, R.drawable.ic_baseline_pause_24);
                mPauseSaveButton.setCompoundDrawablesWithIntrinsicBounds(pauseIcon,null,null,null);
            }
        });

        mCurrentView = findViewById(R.id.current_data);
        mVoltageView = findViewById(R.id.voltage_data);
        mConnectionStatusView = findViewById(R.id.status_info);
        mStatusIcon = findViewById(R.id.status_icon);
        mLeftAxisLabel = findViewById(R.id.left_axis_label);

        mBiasVoltageSpinner = findViewById(R.id.session_bias_voltage_Spinner);
        mBiasVoltageSpinner.setOnItemSelectedListener(this);

        mBiasPolaritySpinner = findViewById(R.id.session_bias_polarity_spinner);
        mBiasPolaritySpinner.setOnItemSelectedListener(this);

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> mBiasVoltageAdapter = ArrayAdapter.createFromResource(this,
                R.array.bias_voltage_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        mBiasVoltageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mBiasVoltageSpinner.setAdapter(mBiasVoltageAdapter);

        ArrayAdapter<CharSequence> mBiasPolarityAdapter = ArrayAdapter.createFromResource(this,
                R.array.bias_polarity_array, android.R.layout.simple_spinner_item);
        mBiasPolarityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBiasPolaritySpinner.setAdapter(mBiasPolarityAdapter);


        final Intent intent = getIntent();
        mSelectedDevice = intent.getParcelableExtra(SELECTED_DEVICE);
        mBiasVoltageIndex = intent.getIntExtra(BIAS_VOLTAGE_INDEX, 2);
        mBiasPolarityIndex = intent.getIntExtra(BIAS_POLARITY_INDEX, 0);
        mTiaGainIndex = intent.getIntExtra(TIA_GAIN_INDEX,7);
        mLoadResistorIndex = intent.getIntExtra(LOAD_RESISTOR_INDEX,3);
        mInternalZeroIndex = intent.getIntExtra(INTERNAL_ZERO_INDEX,0);

        updateRegisters();

        mBiasVoltageSpinner.setSelection(mBiasVoltageIndex);
        mBiasPolaritySpinner.setSelection(mBiasPolarityIndex);

        Log.i(TAG, "voltageIndex: " + mBiasVoltageIndex);
        Log.i(TAG, "polarityIndex: " + mBiasPolarityIndex);

        if (mSelectedDevice == null) {
            Dialog alert = createDialog("Error", "No LactateStat board connected", this);
            alert.show();
        } else {
            mDeviceAddress = mSelectedDevice.getAddress();
        }

        mVoltageView.setText(R.string.loading);
        mCurrentView.setText(R.string.loading);

        // Setup UI reference for the chart
        mChart = findViewById(R.id.lactatestat_chart);

        // Disable description text
        mChart.getDescription().setEnabled(false);

        // Enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(false);

        // set an alternative background color
        mChart.setBackgroundColor(Color.WHITE);

        LineData data = new LineData();
        data.setValueTextColor(Color.BLACK);

        // add empty data
        mChart.setData(data);

        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.BLACK);

        // X-axis setup
        XAxis bottomAxis = mChart.getXAxis();
        bottomAxis.setTextColor(Color.BLACK);
        bottomAxis.setDrawGridLines(true);
        bottomAxis.setPosition(XAxis.XAxisPosition.BOTTOM_INSIDE);
        bottomAxis.setAvoidFirstLastClipping(true);
        bottomAxis.setEnabled(true);

        // Y-axis setup
        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMaximum(1000f);
        leftAxis.setAxisMinimum(-1000f);
        leftAxis.setDrawGridLines(true);
        // Disable right Y-axis
        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

        mChart.getAxisLeft().setDrawGridLines(true);
        mChart.getXAxis().setDrawGridLines(false);
        mChart.setDrawBorders(false);

        feedMultiple();

        Intent mGattServiceIntent = new Intent(SessionActivity.this, BleService.class);
        bindService(mGattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        isStoragePermissionGranted();

    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
        if (mThread != null) {
            mThread.interrupt();
        }
        // When the activity is paused, toggle the button so that the files are closed
        if (mSaveDataSwitch.isChecked()) {
            mSaveDataSwitch.toggle();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        mThread.interrupt();
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        if (!firstStart) {
            mBiasVoltageIndex = mBiasVoltageSpinner.getSelectedItemPosition();
            mBiasPolarityIndex = mBiasPolaritySpinner.getSelectedItemPosition();
            Log.i(TAG, "OnItemSelected: " + mBiasVoltageIndex);
            sendRegisterUpdate();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        // Another interface callback

    }

    // Callback methods to manage the (Ble)Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BleService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.i(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
            Log.i(TAG, "onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            Log.i(TAG, "onServiceDisconnected");
        }
    };

    /*
A method to add our current entries to the chart
 */
    private void addEntry(double current) {
        LineData data = mChart.getData();


        if (data != null) {
            set = data.getDataSetByIndex(0);
            // set.addEntry(.....); Can be called as well
        }

        if (set == null) {
            set = createSet();
            assert data != null;
            data.addDataSet(set);
        }

        assert data != null;
        data.addEntry(new Entry(set.getEntryCount(), (float) current), 0);
        data.notifyDataChanged();

        // let the chart know it's data has changed
        mChart.notifyDataSetChanged();

        // limit the number of visible entries
        mChart.setVisibleXRangeMaximum(30);
        //mChart.setVisibleYRange(0,30, YAxis.AxisDependency.LEFT);

        // move to the latest entry
        //mChart.moveViewToX(data.getEntryCount());
        mChart.moveViewTo(data.getEntryCount(), (float) current, YAxis.AxisDependency.LEFT);
    }

    private LineDataSet createSet() {

        LineDataSet set = new LineDataSet(null, "Current");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(3f);
        set.setColor(Color.RED);
        set.setHighlightEnabled(false);
        set.setDrawValues(false);
        set.setDrawCircles(true);
        set.setCircleRadius(2f);
        return set;
    }

    private void feedMultiple() {

        if (mThread != null) {
            mThread.interrupt();
        }

        mThread = new Thread(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    plotData = true;
                    try {
                        Thread.sleep(900);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        mThread.start();
    }

    // A BroadcastReceiver handling various events fired by the Service, see GattActions.Event.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_GATT_LACTATESTAT_EVENT.equals(action)) {
                GattActions.Event event = (GattActions.Event) intent.getSerializableExtra(EVENT);
                if (event != null) {
                    switch (event) {
                        case GATT_CONNECTED:
                        case GATT_SERVICES_DISCOVERED:
                        case LACTATESTAT_SERVICE_DISCOVERED:
                            mConnectionStatusView.setText(String.format("Connected to: %s", mSelectedDevice.getName()));
                            mConnectionStatusView.setTextColor(getResources().getColor(R.color.connectedColor));
                            mStatusIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_baseline_bluetooth_connected_35));
                            break;
                        case DATA_AVAILABLE:
                            if (!firstStart) {
                                final int adcValue = intent.getIntExtra(LACTATESTAT_DATA, 0);
                                double milliVoltage = adcValue / 1.2412121212121;
                                double current = (milliVoltage - (3300 *
                                        mInternalZeroValues.get(mInternalZeroIndex))) / (mTiaGainValues.get(mTiaGainIndex));
                                mVoltageView.setText(String.format("%.1f mV", milliVoltage));
                                mCurrentView.setText(String.format("%1.1e A", current / 1000));

                                if (plotData) {
                                    addEntry(current * 1000000); // Add our entries in milli ampere
                                    plotData = false;
                                }

                                if (mSaveDataSwitch.isChecked() && !mPauseSaveButton.isChecked()) {
                                    try {
                                        dataSample.write(((float)timeSinceSamplingStart / 1000f + ",").getBytes());
                                        dataSample.write((current + "\n").getBytes());
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            break;
                        case GATT_DISCONNECTED:
                            mConnectionStatusView.setText(R.string.status_not_connected);
                            mConnectionStatusView.setTextColor(getResources().getColor(R.color.disconnectedColor));
                            mStatusIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_baseline_bluetooth_disabled_35));
                            break;
                        case LACTATESTAT_SERVICE_NOT_AVAILABLE:
                            mConnectionStatusView.setText(event.toString());
                            mConnectionStatusView.setTextColor(getResources().getColor(R.color.disconnectedColor));
                            mStatusIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_baseline_bluetooth_disabled_35));
                            break;
                        default:
                            mConnectionStatusView.setText(R.string.device_unreachable);
                            mConnectionStatusView.setTextColor(getResources().getColor(R.color.disconnectedColor));
                            mStatusIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_baseline_bluetooth_disabled_35));
                            break;
                    }
                }
            }
        }
    };

    // Intent filter for broadcast updates from BleService
    private IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_GATT_LACTATESTAT_EVENT);
        return intentFilter;
    }

    public void sendRegisterUpdate() {
        final Intent intent = new Intent(this, BleService.class);
        updateRegisters();
        int registerValues = mTiacnRegister << 8;
        registerValues |= mRefcnRegister;
        intent.putExtra("RegisterValues", registerValues);
        this.startService(intent);
    }

    private void updateRegisters() {
        // Update the TIACN and REFCN registers depending on the received settings.
        // Update REFCN register first
        // Start with bias voltage and bias sign
        mRefcnRegister &= ~(0x1F); // Clear the first five bits so that a bitwise or can be used
        mRefcnRegister |= mBiasVoltageIndex;
        mRefcnRegister |= ((mBiasPolarityIndex << 4) | mBiasVoltageIndex);
        // Update internal zero
        mRefcnRegister &= ~(3 << 5);
        mRefcnRegister |= (mInternalZeroIndex << 5);
        Log.i(TAG, "REFCN REG: " + mRefcnRegister);
        // Now we update the TIACN register
        // We start with TIA gain
        mTiacnRegister &= ~(7 << 2); // Clear bits 2-4
        mTiacnRegister |= (mTiaGainIndex << 2); // Write to bits 2-4
        // Now load resistor
        mTiacnRegister &= ~3; // Clear 0th and 1st bits
        mTiacnRegister |= mLoadResistorIndex;
        Log.i(TAG, "TIACN REG: " + mTiacnRegister);
    }

    // Method to sample data used by the ToggleButton
    private FileOutputStream createFiles() {
        // Get the external storage location
        String root = Environment.getExternalStorageDirectory().toString();
        // Create a new directory
        File myDir = new File(root, "/LactateStat_Data");
        if (!myDir.exists()) {
            myDir.mkdirs();
        }

        String lactateStat = "LactateStat_" + df.format(Calendar.getInstance().getTime()) + ".csv";

        File lactateStatFile = new File(myDir, lactateStat);

        try {
            lactateStatFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            return new FileOutputStream(lactateStatFile, true);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }

    }

    // Helper method to close the files.
    private static void closeFiles(FileOutputStream fo) throws IOException {
        fo.flush();
        fo.close();
    }

    // Method to check if the user has granted access to store data on external memory
    public boolean isStoragePermissionGranted() {
        String TAG = "Storage Permission";
        if (Build.VERSION.SDK_INT >= 23) {
            if (this.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permission is granted");
                return true;
            } else {
                Log.i(TAG, "Permission is revoked");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            Log.i(TAG,"Permission is granted");
            return true;
        }
    }
}
