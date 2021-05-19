package com.example.lactatestat.activities;

// Copyright (c) 2021 Emil Ekelund

import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.lactatestat.R;
import com.example.lactatestat.utilities.MessageUtils;

import java.util.Objects;

import static com.example.lactatestat.utilities.MessageUtils.createDialog;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = BleScanDialog.class.getSimpleName();
    private static final String SELECTED_DEVICE = "selectedDevice";

    private BluetoothDevice mSelectedDevice = null;
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

        mStatusView.setText(R.string.status_not_connected);
        mStatusView.setTextColor(getResources().getColor(R.color.disconnectedColor));
        mStatusIconView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_bluetooth_disabled_35));

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Dashboard");

    }

    @Override
    protected void onPause() {
        super.onPause();
        mStatusView.setText(R.string.status_not_connected);
        mStatusView.setTextColor(getResources().getColor(R.color.disconnectedColor));
        mStatusIconView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_bluetooth_disabled_35));
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

    public void startNewSession(View view) {
        Intent startSettings = new Intent(this, PreStartSettingsActivity.class);
        if (mSelectedDevice != null && !mStatusView.getText().toString().equals("LactateStat not connected")) {
            Log.d(TAG, "status view: " + mStatusView.getText().toString());
            startSettings.putExtra(SELECTED_DEVICE, mSelectedDevice);
            startActivity(startSettings);
        } else {
            Dialog alert = createDialog("Error", "Please connect to LactateStat First", this);
            alert.show();
        }

    }

    public void viewHistory(View view) {
        Intent startHistory = new Intent(this, HistoryActivity.class);
        startActivity(startHistory);
    }
}