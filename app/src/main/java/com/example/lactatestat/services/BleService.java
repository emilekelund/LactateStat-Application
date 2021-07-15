package com.example.lactatestat.services;

// Copyright (c) 2021 Emil Ekelund

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

import com.example.lactatestat.utilities.BitConverter;

import java.util.Arrays;
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
 */

public class BleService extends Service {

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int registerValues = 7936;

    private BluetoothGattService mLactateStatBleService;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        registerValues = intent.getIntExtra("RegisterValues", 7936);
        Log.i(TAG, "RegisterValue: " + registerValues);

        setRegisterValues(registerValues);

        return START_STICKY;
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

                broadcastLactateStatUpdate(Event.GATT_CONNECTED);
                // attempt to discover services
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");

                broadcastLactateStatUpdate(Event.GATT_DISCONNECTED);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {

                broadcastLactateStatUpdate(Event.GATT_SERVICES_DISCOVERED);
                logServices(gatt); // debug

                // get the relevant service
                mLactateStatBleService = gatt.getService(LACTATESTAT_SERVICE);

                if (mLactateStatBleService != null) {
                    broadcastLactateStatUpdate(Event.LACTATESTAT_SERVICE_DISCOVERED);
                    logCharacteristics(mLactateStatBleService);

                    // Enable notifications on the LactateStat measurements
                    BluetoothGattCharacteristic lactateStatData = mLactateStatBleService.getCharacteristic(LACTATESTAT_MEASUREMENT);
                    boolean result = setCharacteristicNotification(
                            lactateStatData, true);
                    Log.i(TAG, "setCharacteristicNotification" + result);

                } else {
                    broadcastLactateStatUpdate(Event.LACTATESTAT_SERVICE_NOT_AVAILABLE);
                    Log.i(TAG, "No relevant service available");
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            // Copy the received byte array so we have a threadsafe copy
            byte[] rawData = new byte[characteristic.getValue().length];

            System.arraycopy(characteristic.getValue(), 0, rawData, 0,
                    characteristic.getValue().length);

            if (characteristic.getValue().length == 4) {
                int adcValue = BitConverter.bytesToInt(rawData);
                broadcastLactateStatData(adcValue);
            } else {
                int[] combinedValues = BitConverter.bytesToIntArray(rawData);
                broadcastCombinedLactateStatData(combinedValues);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            boolean setBias = setRegisterValues(7936);
            Log.i(TAG, "SetRegister: " + setBias);
        }

    };

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
            return mBluetoothGatt.connect();
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

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public boolean setRegisterValues(int registerValues) {
        if (mBluetoothAdapter == null && mBluetoothGatt == null) {
            Log.i(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        BluetoothGattCharacteristic settingsCharacteristic =
                mLactateStatBleService.getCharacteristic(LACTATESTAT_SETTINGS);
        boolean setVal = settingsCharacteristic.setValue(registerValues, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
        Log.i(TAG,"SetValue: " + setVal);

        return mBluetoothGatt.writeCharacteristic(settingsCharacteristic);
    }

    /*
    From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
    Broadcast methods for events and data
     */
    private void broadcastLactateStatUpdate(final Event event) {
        final Intent intent = new Intent(ACTION_GATT_LACTATESTAT_EVENT);
        intent.putExtra(EVENT, event);
        sendBroadcast(intent);
    }

    private void broadcastLactateStatData(final int lactateStatData) {
        final Intent intent = new Intent(ACTION_GATT_LACTATESTAT_EVENT);
        intent.putExtra(EVENT, Event.DATA_AVAILABLE);
        intent.putExtra(LACTATESTAT_DATA, lactateStatData);
        sendBroadcast(intent);
    }

    private void broadcastCombinedLactateStatData(final int[] lactateStatData) {
        final Intent intent = new Intent(ACTION_GATT_LACTATESTAT_EVENT);
        intent.putExtra(EVENT, Event.DATA_AVAILABLE);
        intent.putExtra(LACTATESTAT_DATA, lactateStatData);
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
