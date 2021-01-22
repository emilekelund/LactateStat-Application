package com.example.lactatestat.activities;

import android.bluetooth.BluetoothDevice;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SessionActivity extends AppCompatActivity {
    private static final String TAG = SessionActivity.class.getSimpleName();
    private static final String SELECTED_DEVICE = "selectedDevice";

    private BluetoothDevice mSelectedDevice = null;
    private String mDeviceAddress;

    private TextView mCurrentView;
    private TextView mLactateView;
    private TextView mConnectionStatusView;
    private ImageView mStatusIcon;
    private TextView mBottomAxisLabel;
    private TextView mLeftAxisLabel;


}
