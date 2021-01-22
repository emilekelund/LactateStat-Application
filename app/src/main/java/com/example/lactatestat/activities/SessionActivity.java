package com.example.lactatestat.activities;

import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.lactatestat.R;
import com.example.lactatestat.services.BleService;
import com.example.lactatestat.utilities.MessageUtils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.Objects;

import static com.example.lactatestat.utilities.MessageUtils.createDialog;

public class SessionActivity extends AppCompatActivity {
    private static final String TAG = SessionActivity.class.getSimpleName();
    private static final String SELECTED_DEVICE = "selectedDevice";

    private BluetoothDevice mSelectedDevice = null;
    private BleService mBluetoothLeService;
    private String mDeviceAddress;

    private TextView mCurrentView;
    private TextView mLactateView;
    private TextView mConnectionStatusView;
    private ImageView mStatusIcon;
    private TextView mLeftAxisLabel;

    private ILineDataSet set = null;
    private LineChart mChart;
    private Thread mThread;
    private static boolean plotData = true;

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
        mLactateView = findViewById(R.id.lactate_data);
        mConnectionStatusView = findViewById(R.id.status_info);
        mStatusIcon = findViewById(R.id.bl_status_icon);
        mLeftAxisLabel = findViewById(R.id.left_axis_label);

        final Intent intent = getIntent();
        mSelectedDevice = intent.getParcelableExtra(SELECTED_DEVICE);

        if (mSelectedDevice == null) {
            Dialog alert = createDialog("Error", "No LactateStat board connected", this);
        } else {
            mDeviceAddress = mSelectedDevice.getAddress();
            startBleService();
        }

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

    public void startBleService() {
        Intent mGattServiceIntent = new Intent(this, BleService.class);
        bindService(mGattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }
}
