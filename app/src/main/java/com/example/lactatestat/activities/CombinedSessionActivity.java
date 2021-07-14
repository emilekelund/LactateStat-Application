package com.example.lactatestat.activities;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.lactatestat.R;
import com.example.lactatestat.services.BleService;
import com.example.lactatestat.services.GattActions;
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
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Objects;

import static android.os.Environment.DIRECTORY_DOCUMENTS;
import static com.example.lactatestat.services.GattActions.ACTION_GATT_LACTATESTAT_EVENT;
import static com.example.lactatestat.services.GattActions.EVENT;
import static com.example.lactatestat.services.GattActions.LACTATESTAT_DATA;
import static com.example.lactatestat.utilities.MessageUtils.createDialog;

public class CombinedSessionActivity extends AppCompatActivity {
    private static final String TAG = SessionActivity.class.getSimpleName();
    private static final String SELECTED_DEVICE = "selectedDevice";
    private static final String BIAS_VOLTAGE_INDEX = "biasVoltageIndex";
    private static final String BIAS_POLARITY_INDEX = "biasPolarityIndex";
    private static final String TIA_GAIN_INDEX = "tiaGainIndex";
    private static final String LOAD_RESISTOR_INDEX = "loadResistorIndex";
    private static final String INTERNAL_ZERO_INDEX = "internalZeroIndex";
    private static final String SLOPE = "slope";
    private static final String INTERCEPT = "intercept";

    private BluetoothDevice mSelectedDevice = null;
    private BleService mBluetoothLeService;
    private String mDeviceAddress;

    private TextView mCurrentView;
    private TextView mLactateView;
    private TextView mTemperatureView;
    private TextView mPotentiometerView;
    private TextView mConnectionStatusView;
    private ImageView mStatusIcon;
    private TextView mLeftAxisLabel;

    private Button mSaveDataButton;

    private static final DateFormat df = new SimpleDateFormat("yyMMdd_HH:mm"); // Custom date format for file saving

    private int mBiasVoltageIndex;
    private int mBiasPolarityIndex;
    private int mTiaGainIndex;
    private int mLoadResistorIndex;
    private int mInternalZeroIndex;

    private float slope = 0;
    private float intercept = 0;

    private boolean firstStart = true;

    private int mTiacnRegister = 31;
    private int mRefcnRegister = 2;

    private static final ArrayList<Integer> mTiaGainValues = new ArrayList<>(
            Arrays.asList(1500000, 2750, 3500, 7000, 14000, 35000, 120000, 350000)
    );
    private static final ArrayList<Double> mInternalZeroValues = new ArrayList<>(
            Arrays.asList(0.2, 0.5, 0.67)
    );

    private static final float MULTIPLIER = 0.03125F; // 0.03125 mV per bit

    private final ArrayList<Double> mSampledValues = new ArrayList<>();

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
                    mSamplingTimeTimer.start();
                }
            }.start();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_combined);

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

        mSaveDataButton =  findViewById(R.id.save_data_button);

        mCurrentView = findViewById(R.id.current_data_view);
        mLactateView = findViewById(R.id.lactate_data_view);
        mTemperatureView = findViewById(R.id.temperature_view);
        mPotentiometerView = findViewById(R.id.potentiometer_view);

        mConnectionStatusView = findViewById(R.id.status_info);
        mStatusIcon = findViewById(R.id.status_icon);
        mLeftAxisLabel = findViewById(R.id.left_axis_label);

        final Intent intent = getIntent();
        mSelectedDevice = intent.getParcelableExtra(SELECTED_DEVICE);
        mBiasVoltageIndex = intent.getIntExtra(BIAS_VOLTAGE_INDEX, 2);
        mBiasPolarityIndex = intent.getIntExtra(BIAS_POLARITY_INDEX, 0);
        mTiaGainIndex = intent.getIntExtra(TIA_GAIN_INDEX,7);
        mLoadResistorIndex = intent.getIntExtra(LOAD_RESISTOR_INDEX,3);
        mInternalZeroIndex = intent.getIntExtra(INTERNAL_ZERO_INDEX,0);

        updateRegisters();

        Log.i(TAG, "voltageIndex: " + mBiasVoltageIndex);
        Log.i(TAG, "polarityIndex: " + mBiasPolarityIndex);
        Log.i(TAG, "TIA index: " + mTiaGainIndex);
        Log.i(TAG, "Internal z index:" + mInternalZeroIndex);

        if (mSelectedDevice == null) {
            Dialog alert = createDialog("Error", "No LactateStat board connected", this);
            alert.show();
        } else {
            mDeviceAddress = mSelectedDevice.getAddress();
        }

        mLactateView.setText(R.string.loading);
        mCurrentView.setText(R.string.loading);
        mTemperatureView.setText(R.string.loading);
        mPotentiometerView.setText(R.string.loading);

        slope = intent.getFloatExtra(SLOPE, slope);
        intercept = intent.getFloatExtra(INTERCEPT, intercept);

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
        leftAxis.setAxisMaximum(25f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);
        // Disable right Y-axis
        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

        mChart.getAxisLeft().setDrawGridLines(true);
        mChart.getXAxis().setDrawGridLines(false);
        mChart.setDrawBorders(false);

        feedMultiple();

        Intent mGattServiceIntent = new Intent(CombinedSessionActivity.this, BleService.class);
        bindService(mGattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
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
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        mThread.interrupt();
        mSamplingTimeTimer.cancel();
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
    private void addEntry(double lactate) {
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
        data.addEntry(new Entry(set.getEntryCount(), (float) lactate), 0);
        data.notifyDataChanged();

        // let the chart know it's data has changed
        mChart.notifyDataSetChanged();

        // limit the number of visible entries
        mChart.setVisibleXRangeMaximum(2000);
        //mChart.setVisibleYRange(0,30, YAxis.AxisDependency.LEFT);

        // move to the latest entry
        //mChart.moveViewToX(data.getEntryCount());
        mChart.moveViewTo(data.getEntryCount(), (float) lactate, YAxis.AxisDependency.LEFT);
    }

    private LineDataSet createSet() {

        LineDataSet set = new LineDataSet(null, "Lactate");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(2f);
        set.setColor(Color.RED);
        set.setHighlightEnabled(false);
        set.setDrawValues(false);
        set.setDrawCircles(false);
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
                            mConnectionStatusView.setText(R.string.connected_to_lactatestat);
                            mConnectionStatusView.setTextColor(getResources().getColor(R.color.connectedColor));
                            mStatusIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_baseline_bluetooth_connected_35));
                            break;
                        case DATA_AVAILABLE:
                            if (!firstStart) {
                                final int[] combinedValues = intent.getIntArrayExtra(LACTATESTAT_DATA);
                                double milliVoltage = combinedValues[0] / 1.2412121212121;
                                double current = (milliVoltage - (3300 *
                                        mInternalZeroValues.get(mInternalZeroIndex))) / (mTiaGainValues.get(mTiaGainIndex));
                                double lactate = currentToLactate((current / 1000));
                                mLactateView.setText(String.format("%.1f mM", lactate));
                                mCurrentView.setText(String.format("%1.1e A", current / 1000));

                                double resistance = (combinedValues[1] * 6650.0) / Math.pow(2,16) * 2;
                                double temperature = (resistance - 1000) / (1000 * 0.00385);
                                double potential = combinedValues[2] * MULTIPLIER;
                                mPotentiometerView.setText(String.format("%.1f mV", potential));
                                mTemperatureView.setText(String.format("%.1f\u00B0C", temperature));

                                if (plotData) {
                                    addEntry(lactate);
                                    plotData = false;
                                }

                                mSampledValues.add((double) (timeSinceSamplingStart/1000));
                                mSampledValues.add((double) current / 1000);
                                mSampledValues.add(lactate);
                                mSampledValues.add(potential);
                                mSampledValues.add(temperature);

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

    private double currentToLactate(double current) {
        return (slope * current) + intercept;
    }

    // Request code for creating a csv file.
    private static final int CREATE_FILE = 1;

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createFile() {
        final File dir;
        if (Build.VERSION_CODES.R > Build.VERSION.SDK_INT) {
            dir = new File(Environment.getExternalStorageDirectory().getPath()
                    + "/LactateStat_Data");
        } else {
            dir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).getPath()
                    + "/LactateStat_Data");
        }

        String fileName = "LactateStat_" + df.format(Calendar.getInstance().getTime());

        if (!dir.exists())
            dir.mkdir();

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        startActivityForResult(intent, CREATE_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == 1
                && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                try {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    outputStream.write(("Time (s), Current (A), Lactate (mM), Potential (mV), Temperature (\u00B0C)").getBytes());
                    // Write our sampled values to the created file
                    for (int i = 0; i < mSampledValues.size(); i+=5){
                        try {
                            outputStream.write((mSampledValues.get(i) + ",").getBytes());
                            outputStream.write((mSampledValues.get(i+1) + ",").getBytes());
                            outputStream.write((mSampledValues.get(i+2) + ",").getBytes());
                            outputStream.write((mSampledValues.get(i+3) + ",").getBytes());
                            outputStream.write((mSampledValues.get(i+4) + "\n").getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void saveData(View view) {
        createFile();
    }
}
