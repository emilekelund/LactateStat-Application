package com.example.lactatestat.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.example.lactatestat.adapters.bleDeviceAdapter;

import java.util.ArrayList;
import java.util.List;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
import static com.example.lactatestat.services.LactateStatUUIDs.LACTATESTAT_SERVICE;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class bleScanDialog extends AppCompatActivity {
    private static final String TAG = bleScanDialog.class.getSimpleName();

    public static final int REQUEST_ENABLE_BT = 1000;
    public static final int REQUEST_ACCESS_LOCATION = 1001;

    public static String SELECTED_DEVICE = "Selected device";

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private ArrayList<BluetoothDevice> mDeviceList;
    private bleDeviceAdapter mBtDeviceAdapter;
    private TextView mScanInfoView;

    private static final long SCAN_PERIOD = 15000; // 15 seconds

    // We are only interested in devices with our service, so we create a scan filter
    private static final List<ScanFilter> LACTATESTAT_SCAN_FILTER;
    private static final ScanSettings SCAN_SETTINGS;

    static {
        ScanFilter lactateStatServiceFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(LACTATESTAT_SERVICE))
                .build();
        LACTATESTAT_SCAN_FILTER = new ArrayList<>();
        LACTATESTAT_SCAN_FILTER.add(lactateStatServiceFilter);
        SCAN_SETTINGS = new ScanSettings.Builder()
                .setScanMode(CALLBACK_TYPE_ALL_MATCHES).build();
    }


}
