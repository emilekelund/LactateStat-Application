package com.example.lactatestat.activities;

// Copyright (c) 2021 Emil Ekelund

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.lactatestat.R;
import com.example.lactatestat.services.BleService;
import com.example.lactatestat.services.GattActions;
import com.example.lactatestat.utilities.MessageUtils;

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

public class CalibrationActivity extends AppCompatActivity {
    private static final String TAG = SessionActivity.class.getSimpleName();
    private static final String SELECTED_DEVICE = "selectedDevice";
    private static final String BIAS_VOLTAGE_INDEX = "biasVoltageIndex";
    private static final String BIAS_POLARITY_INDEX = "biasPolarityIndex";
    private static final String TIA_GAIN_INDEX = "tiaGainIndex";
    private static final String LOAD_RESISTOR_INDEX = "loadResistorIndex";
    private static final String INTERNAL_ZERO_INDEX = "internalZeroIndex";
    private static final String SLOPE = "slope";
    private static final String INTERCEPT = "intercept";
    private static final String CALIBRATION_DONE = "calibrationDone";

    private BluetoothDevice mSelectedDevice = null;
    private BleService mBluetoothLeService;
    private String mDeviceAddress;

    private TextView currentValue;
    private TextView mConnectionStatusView;
    private TextView currentInput1;
    private TextView currentInput2;
    private TextView currentInput3;
    private TextView currentInput4;
    private TextView currentInput5;
    private TextView currentInput6;
    private TextView lactateInput1;
    private TextView lactateInput2;
    private TextView lactateInput3;
    private TextView lactateInput4;
    private TextView lactateInput5;
    private TextView lactateInput6;
    private TextView calibrationResult;

    private ImageView mConnectionStatusIcon;

    private static final DateFormat df = new SimpleDateFormat("yyMMdd_HH:mm"); // Custom date format for file saving

    private Button mCalibrateButton;
    private Button mSaveCalibrationButton;

    private int mBiasVoltageIndex;
    private int mBiasPolarityIndex;
    private int mTiaGainIndex;
    private int mLoadResistorIndex;
    private int mInternalZeroIndex;
    private int mTiacnRegister = 31;
    private int mRefcnRegister = 2;

    private static float slope;
    private static float intercept;

    private boolean firstStart = true;

    private static final ArrayList<Integer> mTiaGainValues = new ArrayList<>(
            Arrays.asList(1500000, 2750, 3500, 7000, 14000, 35000, 120000, 350000)
    );
    private static final ArrayList<Double> mInternalZeroValues = new ArrayList<>(
            Arrays.asList(0.2, 0.5, 0.67)
    );

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
        setContentView(R.layout.activity_calibrate);

        // TOOLBAR SETUP
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("LactateStat calibration");
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

        currentValue = findViewById(R.id.current_view);
        currentInput1 = findViewById(R.id.current_input_1);
        currentInput2 = findViewById(R.id.current_input_2);
        currentInput3 = findViewById(R.id.current_input_3);
        currentInput4 = findViewById(R.id.current_input_4);
        currentInput5 = findViewById(R.id.current_input_5);
        currentInput6 = findViewById(R.id.current_input_6);
        lactateInput1 = findViewById(R.id.lactate_input_1);
        lactateInput2 = findViewById(R.id.lactate_input_2);
        lactateInput3 = findViewById(R.id.lactate_input_3);
        lactateInput4 = findViewById(R.id.lactate_input_4);
        lactateInput5 = findViewById(R.id.lactate_input_5);
        lactateInput6 = findViewById(R.id.lactate_input_6);

        mConnectionStatusView = findViewById(R.id.status_info);
        mConnectionStatusIcon = findViewById(R.id.status_icon);
        calibrationResult = findViewById(R.id.calibration_result);

        mCalibrateButton = findViewById(R.id.start_calibration);
        mSaveCalibrationButton = findViewById(R.id.save_calibration_button);

        final Intent intent = getIntent();
        mSelectedDevice = intent.getParcelableExtra(SELECTED_DEVICE);
        mBiasVoltageIndex = intent.getIntExtra(BIAS_VOLTAGE_INDEX, 2);
        mBiasPolarityIndex = intent.getIntExtra(BIAS_POLARITY_INDEX, 0);
        mTiaGainIndex = intent.getIntExtra(TIA_GAIN_INDEX,7);
        mLoadResistorIndex = intent.getIntExtra(LOAD_RESISTOR_INDEX,3);
        mInternalZeroIndex = intent.getIntExtra(INTERNAL_ZERO_INDEX,0);

        updateRegisters();

        if (mSelectedDevice == null) {
            Dialog alert = createDialog("Error", "No LactateStat board connected", this);
            alert.show();
        } else {
            mDeviceAddress = mSelectedDevice.getAddress();
        }

        currentValue.setText(R.string.loading);

        Intent mGattServiceIntent = new Intent(CalibrationActivity.this, BleService.class);
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
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
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
                            mConnectionStatusIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_baseline_bluetooth_connected_35));
                            break;
                        case DATA_AVAILABLE:
                            if (!firstStart) {
                                final int adcValue = intent.getIntExtra(LACTATESTAT_DATA, 0);
                                double milliVoltage = adcValue / 1.2412121212121;
                                double current = (milliVoltage - (3300 *
                                        mInternalZeroValues.get(mInternalZeroIndex))) / (mTiaGainValues.get(mTiaGainIndex));

                                currentValue.setText(String.format("%.1f nA", current * 1000000));

                            }
                            break;
                        case GATT_DISCONNECTED:
                            mConnectionStatusView.setText(R.string.status_not_connected);
                            mConnectionStatusView.setTextColor(getResources().getColor(R.color.disconnectedColor));
                            mConnectionStatusIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_baseline_bluetooth_disabled_35));
                            break;
                        case LACTATESTAT_SERVICE_NOT_AVAILABLE:
                            mConnectionStatusView.setText(event.toString());
                            mConnectionStatusView.setTextColor(getResources().getColor(R.color.disconnectedColor));
                            mConnectionStatusIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_baseline_bluetooth_disabled_35));
                            break;
                        default:
                            mConnectionStatusView.setText(R.string.device_unreachable);
                            mConnectionStatusView.setTextColor(getResources().getColor(R.color.disconnectedColor));
                            mConnectionStatusIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_baseline_bluetooth_disabled_35));
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

    public void sendRegisterUpdate() {
        final Intent intent = new Intent(this, BleService.class);
        updateRegisters();
        int registerValues = mTiacnRegister << 8;
        registerValues |= mRefcnRegister;
        intent.putExtra("RegisterValues", registerValues);
        this.startService(intent);
    }

    public void calibrate(View view) {
        float current1, current2, current3, current4, current5, current6;
        float lactate1, lactate2, lactate3, lactate4, lactate5, lactate6;

        String current1String = currentInput1.getText().toString();
        String current2String = currentInput2.getText().toString();
        String current3String = currentInput3.getText().toString();
        String current4String = currentInput4.getText().toString();
        String current5String = currentInput5.getText().toString();
        String current6String = currentInput6.getText().toString();

        String lactate1String = lactateInput1.getText().toString();
        String lactate2String = lactateInput2.getText().toString();
        String lactate3String = lactateInput3.getText().toString();
        String lactate4String = lactateInput4.getText().toString();
        String lactate5String = lactateInput5.getText().toString();
        String lactate6String = lactateInput6.getText().toString();

        if (current1String.length() == 0 || current2String.length() == 0 || current3String.length() == 0
            || current4String.length() == 0 || current5String.length() == 0 || current6String.length() == 0
            || lactate1String.length() == 0 || lactate2String.length() == 0 || lactate3String.length() == 0
            || lactate4String.length() == 0 || lactate5String.length() == 0 || lactate6String.length() == 0) {
            MessageUtils.showToast("Please enter values in all calibration boxes", this);
        } else {
            current1 = Float.parseFloat(current1String) / 1000000000; // The value is entered in nanoampere
            current2 = Float.parseFloat(current2String) / 1000000000;
            current3 = Float.parseFloat(current3String) / 1000000000;
            current4 = Float.parseFloat(current4String) / 1000000000;
            current5 = Float.parseFloat(current5String) / 1000000000;
            current6 = Float.parseFloat(current6String) / 1000000000;
            lactate1 = Float.parseFloat(lactate1String);
            lactate2 = Float.parseFloat(lactate2String);
            lactate3 = Float.parseFloat(lactate3String);
            lactate4 = Float.parseFloat(lactate4String);
            lactate5 = Float.parseFloat(lactate5String);
            lactate6 = Float.parseFloat(lactate6String);


            float a = 5 * ((current1 * lactate1) + (current2 * lactate2) + (current3 * lactate3)
                        + (current4 * lactate4) + (current5 * lactate5) + (current6 * lactate6));

            float b = (current1 + current2 + current3 + current4 + current5 + current6)
                    * (lactate1 + lactate2 + lactate3 + lactate4 + lactate5 + lactate6);

            float c = (float) (6 * (Math.pow(current1, 2) + Math.pow(current2, 2) + Math.pow(current3, 2)
                    + Math.pow(current4, 2) + Math.pow(current5, 2) + Math.pow(current6, 2)));

            float d = (float) Math.pow(current1 + current2 + current3 + current4 + current5 + current6, 2);

            slope = (a - b) / (c - d);

            float e = (lactate1 + lactate2 + lactate3 + lactate4 + lactate5 + lactate6);
            float f = slope * (current1 + current2 + current3 + current4 + current5 + current6);

            intercept = (e - f) / 6;

            hideKeyboard(view);

            calibrationResult.setText(String.format("f(x) = %.1ex + %.1f", slope, intercept));
        }
    }

    private void hideKeyboard(View v) {
        InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(v.getApplicationWindowToken(),0);
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

                    // Write our slope and intercept values to the created file
                    outputStream.write((slope + ",").getBytes());
                    outputStream.write((intercept + "\n").getBytes());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Intent returnIntent = new Intent();
                returnIntent.putExtra(CALIBRATION_DONE, 5);
                returnIntent.putExtra(SLOPE, slope);
                returnIntent.putExtra(INTERCEPT, intercept);
                setResult(RESULT_OK, returnIntent);
                finish();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void saveCalibration(View view) {
        createFile();
    }
}
