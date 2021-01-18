package com.example.lactatestat.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.List;

import static com.example.lactatestat.services.GattActions.*;
import static com.example.lactatestat.services.LactateStatUUIDs.*;

/**
 * This is an Android Service component communicating with the the Ble Gatt
 * server on a LactateStat board, by implementing the BluetoothGattCallback
 * interface. The events and data from the LactateStat device are broadcast;
 * the broadcasts are intercepted by the LactateStat activity.
 * <p>
 * NB! Do not to confuse this Android Service with the Ble Gatt service on
 * the LactateStat board.
 * This class is based on the example https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
 * but with relevant modifications made to fit this specific project
 */

public class BleService extends Service {

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    private BluetoothGattService mLactateStatService = null;

    /**
     * From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection
     * result is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(...)} callback.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device - try to reconnect
        if (address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            boolean result = mBluetoothGatt.connect();
            return result;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
     * After using a given BLE device, the app must call this method to ensure resources
     * are released properly.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }


    // Callback method for the BluetoothGatt
    // From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service with modifications
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void onConnectionStateChange(
                BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");

                broadcastUpdate(Event.GATT_CONNECTED);
                // attempt to discover services
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");

                broadcastUpdate(Event.GATT_DISCONNECTED);
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {

                broadcastUpdate(Event.GATT_SERVICES_DISCOVERED);
                logServices(gatt); // debug

                // get the LactateStat service
                mLactateStatService = gatt.getService(LACTATESTAT_SERVICE);

                if (mLactateStatService != null) {
                    broadcastUpdate(Event.LACTATESTAT_SERVICE_DISCOVERED);
                    logCharacteristics(mLactateStatService); // debug

                    // Enable notifications on our LactateStat measurements
                    BluetoothGattCharacteristic LactateStatData =
                            mLactateStatService.getCharacteristic(LACTATESTAT_MEASUREMENT);
                    boolean result = setCharacteristicNotification(
                            LactateStatData, true);
                    Log.i(TAG, "Enable characteristic notifications: " + result);
                } else {
                    broadcastUpdate(Event.LACTATESTAT_SERVICE_NOT_AVAILABLE);
                    Log.i(TAG, "LactateStat service not available");
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {

        }

    };

    /**
     * From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
     * Enables or disables notification on a given characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public boolean setCharacteristicNotification(
            BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.i(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        // first: call setCharacteristicNotification (client side)
        boolean result = mBluetoothGatt.setCharacteristicNotification(
                characteristic, enabled);

        // second: set enable notification server side (sensor)
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                CLIENT_CHARACTERISTIC_CONFIG);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
        return result;
    }

    /*
    From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
    Broadcast methods for events and data
     */
    private void broadcastUpdate(final Event event) {
        final Intent intent = new Intent(ACTION_GATT_LACTATESTAT_EVENT);
        intent.putExtra(EVENT, event);
        sendBroadcast(intent);
    }

    // Broadcast the new LactateStat data to our Intent
    // Based on https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
    private void broadcastLactateStatUpdate(final double[] elevation) {
        final Intent intent = new Intent(ACTION_GATT_LACTATESTAT_EVENT);
        intent.putExtra(EVENT, Event.DATA_AVAILABLE);
        intent.putExtra(LACTATESTAT_DATA, elevation);
        sendBroadcast(intent);
    }

    /*
    From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
    Android Service specific code for binding and unbinding to this Android service
     */
    public class LocalBinder extends Binder {
        public BleService getService() {

            return BleService.this;
        }
    }


    // From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close()
        // is called such that resources are cleaned up properly.  In this particular
        // example, close() is invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /*
    From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
    logging and debugging
     */
    private final static String TAG = BleService.class.getSimpleName();

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void logServices(BluetoothGatt gatt) {
        List<BluetoothGattService> services = gatt.getServices();
        for (BluetoothGattService service : services) {
            String uuid = service.getUuid().toString();
            Log.i(TAG, "service: " + uuid);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void logCharacteristics(BluetoothGattService gattService) {
        List<BluetoothGattCharacteristic> characteristics =
                gattService.getCharacteristics();
        for (BluetoothGattCharacteristic chara : characteristics) {
            String uuid = chara.getUuid().toString();
            Log.i(TAG, "characteristic: " + uuid);
        }
    }


}
