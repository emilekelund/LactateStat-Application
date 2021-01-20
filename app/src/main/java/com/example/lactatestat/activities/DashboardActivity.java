package com.example.lactatestat.activities;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.lactatestat.R;
import com.example.lactatestat.services.BleService;
import com.example.lactatestat.utilities.MessageUtils;

import java.util.Objects;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = bleScanDialog.class.getSimpleName();
    public static String SELECTED_DEVICE = "Selected device";

    private BluetoothDevice mSelectedDevice = null;
    private BleService mBluetoothLeService;
    private TextView mStatusView;
    private String mDeviceAddress;

    static final int SCAN_DEVICE_REQUEST = 0;



    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mStatusView = findViewById(R.id.connection_info);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Dashboard");

        // Bind to the BleService
        Intent gattServiceIntent = new Intent(this, BleService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    /*
    Callback methods to manage the (BleImu)Service lifecycle.
    */
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

    public void startBleSearch(View view) {
        Intent start_scan = new Intent(DashboardActivity.this, bleScanDialog.class);
        startActivityForResult(start_scan, SCAN_DEVICE_REQUEST);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == SCAN_DEVICE_REQUEST) {
            if (resultCode == RESULT_OK) {
                mSelectedDevice = data.getParcelableExtra(bleScanDialog.SELECTED_DEVICE);
                if (mSelectedDevice == null) {
                    MessageUtils.createDialog("Error", "No device found", this).show();
                } else {
                    mStatusView.setText(String.format("Connected to: %s", mSelectedDevice.getName()));
                    mDeviceAddress = mSelectedDevice.getAddress();
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


}