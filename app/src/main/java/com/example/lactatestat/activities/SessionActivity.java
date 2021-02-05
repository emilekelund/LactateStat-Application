package com.example.lactatestat.activities;

import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

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

    private BluetoothDevice mSelectedDevice = null;
    private BleService mBluetoothLeService;
    private String mDeviceAddress;

    private TextView mCurrentView;
    private TextView mVoltageView;
    private TextView mConnectionStatusView;
    private ImageView mStatusIcon;
    private TextView mLeftAxisLabel;

    private Spinner mBiasVoltageSpinner;
    private Spinner mBiasPolaritySpinner;

    private int mBiasVoltageIndex;
    private int mBiasPolarityIndex;

    private ILineDataSet set = null;
    private LineChart mChart;
    private Thread mThread;
    private static boolean plotData = true;

    private boolean connected = false;

    private long timeSinceSamplingStart = 0;

    private final CountDownTimer mCountDownTimer = new
            CountDownTimer(86400000, 50) {
                @Override
                public void onTick(long millisUntilFinished) {
                    timeSinceSamplingStart = 86400000 - millisUntilFinished;
                }

                @Override
                public void onFinish() {

                }
            };

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
        }
        myToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
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

        mBiasVoltageSpinner.setSelection(2);

        ArrayAdapter<CharSequence> mBiasPolarityAdapter = ArrayAdapter.createFromResource(this,
                R.array.bias_polarity_array, android.R.layout.simple_spinner_item);
        mBiasPolarityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBiasPolaritySpinner.setAdapter(mBiasPolarityAdapter);


        final Intent intent = getIntent();
        mSelectedDevice = intent.getParcelableExtra(SELECTED_DEVICE);
        mBiasVoltageIndex = intent.getIntExtra(BIAS_VOLTAGE_INDEX, 6);
        mBiasPolarityIndex = intent.getIntExtra(BIAS_POLARITY_INDEX, 1);

        mBiasVoltageSpinner.setSelection(mBiasVoltageIndex);
        mBiasPolaritySpinner.setSelection(mBiasPolarityIndex);

        if (mSelectedDevice == null) {
            Dialog alert = createDialog("Error", "No LactateStat board connected", this);
            alert.show();
        } else {
            mDeviceAddress = mSelectedDevice.getAddress();
        }

        Intent mGattServiceIntent = new Intent(this, BleService.class);
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
        if (mBluetoothLeService != null) {
            unbindService(mServiceConnection);
            mBluetoothLeService = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothLeService != null) {
            unbindService(mServiceConnection);
            mBluetoothLeService = null;
        }

    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        mBiasVoltageIndex = mBiasVoltageSpinner.getSelectedItemPosition();
        mBiasPolarityIndex = mBiasPolaritySpinner.getSelectedItemPosition();
        Log.i(TAG, "OnItemSelected: " + mBiasVoltageIndex);
        if (connected) {
            setBiasValue(mBiasVoltageIndex);
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
                            connected = true;
                            break;
                        case DATA_AVAILABLE:
                            final int adcValue = intent.getIntExtra(LACTATESTAT_DATA, 0);
                            double voltage = adcValue / 1.13777777778;
                            double current = (voltage - (3600 * 0.2)) / 350000;
                            mVoltageView.setText(String.format("%.2f mV", voltage));
                            mCurrentView.setText(String.format("%.0f nA", current * Math.pow(10, 6)));
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

    public void setBiasValue(int biasValue) {
        final Intent intent = new Intent(this, BleService.class);
        intent.putExtra("BiasValue", biasValue);
        this.startService(intent);
    }
}
