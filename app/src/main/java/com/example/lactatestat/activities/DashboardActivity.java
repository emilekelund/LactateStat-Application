package com.example.lactatestat.activities;

import android.annotation.SuppressLint;
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
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.lactatestat.R;
import com.example.lactatestat.services.BleService;
import com.example.lactatestat.services.GattActions;
import com.example.lactatestat.utilities.MessageUtils;

import java.util.Objects;

import static com.example.lactatestat.services.GattActions.ACTION_GATT_LACTATESTAT_EVENT;
import static com.example.lactatestat.services.GattActions.EVENT;
import static com.example.lactatestat.utilities.MessageUtils.createDialog;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = BleScanDialog.class.getSimpleName();
    private static final String DEVICE_ADDRESS = "deviceAddress";
    private static final String SELECTED_DEVICE = "selectedDevice";

    private BluetoothDevice mSelectedDevice;
    private TextView mStatusView;
    private String mDeviceAddress;
    private ImageView mStatusIconView;

    static final int SCAN_DEVICE_REQUEST = 0;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mStatusView = findViewById(R.id.connection_info);
        mStatusIconView = findViewById(R.id.bl_status_icon);

        if (savedInstanceState != null) {
            mDeviceAddress = savedInstanceState.getString(DEVICE_ADDRESS);
            mSelectedDevice = savedInstanceState.getParcelable(SELECTED_DEVICE);
        } else {
            mSelectedDevice = null;
        }

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Dashboard");

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    public void startBleSearch(View view) {
        Intent startScan = new Intent(DashboardActivity.this, BleScanDialog.class);
        startActivityForResult(startScan, SCAN_DEVICE_REQUEST);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == SCAN_DEVICE_REQUEST) {
            if (resultCode == RESULT_OK) {
                mSelectedDevice = data.getParcelableExtra(BleScanDialog.SELECTED_DEVICE);
                if (mSelectedDevice == null) {
                    MessageUtils.createDialog("Error", "No device found", this).show();
                } else {
                    mStatusView.setText(R.string.ready_to_start);
                    mStatusView.setTextColor(getResources().getColor(R.color.connectedColor));
                    mStatusIconView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_bluetooth_connected_35));
                    mDeviceAddress = mSelectedDevice.getAddress();
                    Log.i(TAG, "DeviceAddress: " + mDeviceAddress);
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(DEVICE_ADDRESS, mDeviceAddress);
        outState.putParcelable(SELECTED_DEVICE, mSelectedDevice);
        super.onSaveInstanceState(outState);
    }

    public void startNewSession(View view) {
        Intent startSession = new Intent(this, SessionActivity.class);
        if (mSelectedDevice != null && !mStatusView.getText().toString().equals("LactateStat not connected")) {
            Log.d(TAG, "status view: " + mStatusView.getText().toString());
            startSession.putExtra(SELECTED_DEVICE, mSelectedDevice);
            startActivity(startSession);
        } else {
            Dialog alert = createDialog("Error", "Please connect to LactateStat First", this);
            alert.show();
        }

    }
}