package com.example.lactatestat.activities;

// Copyright (c) 2021 Emil Ekelund

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.lactatestat.R;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;

import static com.example.lactatestat.utilities.MessageUtils.createDialog;

public class PreStartSettingsActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    private static final String TAG = PreStartSettingsActivity.class.getSimpleName();
    private static final String SELECTED_DEVICE = "selectedDevice";
    private static final String BIAS_VOLTAGE_INDEX = "biasVoltageIndex";
    private static final String BIAS_POLARITY_INDEX = "biasPolarityIndex";
    private static final String TIA_GAIN_INDEX = "tiaGainIndex";
    private static final String LOAD_RESISTOR_INDEX = "loadResistorIndex";
    private static final String INTERNAL_ZERO_INDEX = "internalZeroIndex";
    private static final String SLOPE = "slope";
    private static final String INTERCEPT = "intercept";

    private String mDeviceName;

    private TextView mCalibrationInfo;

    private BluetoothDevice mSelectedDevice = null;
    private Spinner mBiasVoltageSpinner;
    private Spinner mBiasPolaritySpinner;
    private Spinner mTiaGainSpinner;
    private Spinner mLoadResistorSpinner;
    private Spinner mInternalZeroSpinner;
    private int mBiasVoltageIndex = 2;
    private int mBiasPolarityIndex = 0;
    private int mTiaGainIndex = 7;
    private int mLoadResistorIndex = 3;
    private int mInternalZeroIndex = 0;

    private boolean calibrationDone = false;

    private float slope = 0;
    private float intercept = 0;

    private static final int CALIBRATION_REQUEST_CODE = 1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pre_start_settings);

        // TOOLBAR SETUP
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Potentiostat settings");
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

        mCalibrationInfo = findViewById(R.id.info_text);

        mBiasVoltageSpinner = findViewById(R.id.bias_voltage_spinner);
        mBiasVoltageSpinner.setOnItemSelectedListener(this);

        mBiasPolaritySpinner = findViewById(R.id.bias_polarity_spinner);
        mBiasPolaritySpinner.setOnItemSelectedListener(this);

        mTiaGainSpinner = findViewById(R.id.tia_gain_spinner);
        mTiaGainSpinner.setOnItemSelectedListener(this);

        mLoadResistorSpinner = findViewById(R.id.load_resistor_spinner);
        mLoadResistorSpinner.setOnItemSelectedListener(this);

        mInternalZeroSpinner = findViewById(R.id.internal_zero_spinner);
        mInternalZeroSpinner.setOnItemSelectedListener(this);

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> mBiasVoltageAdapter = ArrayAdapter.createFromResource(this,
                R.array.bias_voltage_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        mBiasVoltageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        mBiasVoltageSpinner.setAdapter(mBiasVoltageAdapter);

        ArrayAdapter<CharSequence> mBiasPolarityAdapter = ArrayAdapter.createFromResource(this,
                R.array.bias_polarity_array, android.R.layout.simple_spinner_item);
        mBiasPolarityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBiasPolaritySpinner.setAdapter(mBiasPolarityAdapter);

        ArrayAdapter<CharSequence> mTiaGainAdapter = ArrayAdapter.createFromResource(this,
                R.array.tia_gain_array, android.R.layout.simple_spinner_item);
        mTiaGainAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTiaGainSpinner.setAdapter(mTiaGainAdapter);

        ArrayAdapter<CharSequence> mLoadResistorAdapter = ArrayAdapter.createFromResource(this,
                R.array.load_resistor_array, android.R.layout.simple_spinner_item);
        mLoadResistorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mLoadResistorSpinner.setAdapter(mLoadResistorAdapter);

        ArrayAdapter<CharSequence> mInternalZeroAdapter = ArrayAdapter.createFromResource(this,
                R.array.internal_zero_array, android.R.layout.simple_spinner_item);
        mInternalZeroAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mInternalZeroSpinner.setAdapter(mInternalZeroAdapter);

        mBiasVoltageSpinner.setSelection(mBiasVoltageIndex);
        mBiasPolaritySpinner.setSelection(mBiasPolarityIndex);
        mTiaGainSpinner.setSelection(mTiaGainIndex);
        mLoadResistorSpinner.setSelection(mLoadResistorIndex);
        mInternalZeroSpinner.setSelection(mInternalZeroIndex);

        final Intent intent = getIntent();
        mSelectedDevice = intent.getParcelableExtra(SELECTED_DEVICE);

        if (mSelectedDevice == null) {
            Dialog alert = createDialog("Error", "No LactateStat board connected", this);
            alert.show();
        } else {
            String mDeviceAddress = mSelectedDevice.getAddress();
            mDeviceName = mSelectedDevice.getName();
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        mBiasVoltageIndex = mBiasVoltageSpinner.getSelectedItemPosition();
        mBiasPolarityIndex = mBiasPolaritySpinner.getSelectedItemPosition();
        mTiaGainIndex = mTiaGainSpinner.getSelectedItemPosition();
        mLoadResistorIndex = mLoadResistorSpinner.getSelectedItemPosition();
        mInternalZeroIndex = mInternalZeroSpinner.getSelectedItemPosition();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        // Another interface callback
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CALIBRATION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                slope = data.getFloatExtra(SLOPE, 0);
                intercept = data.getFloatExtra(INTERCEPT, 0);
                mCalibrationInfo.setText(String.format("New calibration done: f(x) = %.1e + %.1f\nReady to start session.", slope, intercept));
                calibrationDone = true;
            }

        }

        if (requestCode == 2
                && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that the user selected
            Uri uri = null;
            if (data != null) {
                uri = data.getData();
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    // Read the opened values from the inputStream

                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] rowData = line.split(",");
                        slope = Float.parseFloat(rowData[0]);
                        intercept = Float.parseFloat(rowData[1]);
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }

                mCalibrationInfo.setText(String.format("Calibration loaded: f(x) = %.1e + %.1f\nReady to start session.", slope, intercept));
                calibrationDone = true;

            }
        }

    }

    public void startSession(View view) {
        Intent startSession = null;

        if (mDeviceName.contains("Combined")) {
            startSession = new Intent(this, CombinedSessionActivity.class);
        } else {
            startSession = new Intent(this, SessionActivity.class);
        }

        startSession.putExtra(SELECTED_DEVICE, mSelectedDevice);
        startSession.putExtra(BIAS_VOLTAGE_INDEX, mBiasVoltageIndex);
        startSession.putExtra(BIAS_POLARITY_INDEX, mBiasPolarityIndex);
        startSession.putExtra(TIA_GAIN_INDEX, mTiaGainIndex);
        startSession.putExtra(LOAD_RESISTOR_INDEX, mLoadResistorIndex);
        startSession.putExtra(INTERNAL_ZERO_INDEX, mInternalZeroIndex);

        if (slope != 0 || intercept != 0) {
            startSession.putExtra(SLOPE, slope);
            startSession.putExtra(INTERCEPT, intercept);
        }

        startActivity(startSession);
    }

    public void newCalibration(View view) {
        Intent startNewCalibration = new Intent(this, CalibrationActivity.class);
        startNewCalibration.putExtra(SELECTED_DEVICE, mSelectedDevice);
        startNewCalibration.putExtra(BIAS_VOLTAGE_INDEX, mBiasVoltageIndex);
        startNewCalibration.putExtra(BIAS_POLARITY_INDEX, mBiasPolarityIndex);
        startNewCalibration.putExtra(TIA_GAIN_INDEX, mTiaGainIndex);
        startNewCalibration.putExtra(LOAD_RESISTOR_INDEX, mLoadResistorIndex);
        startNewCalibration.putExtra(INTERNAL_ZERO_INDEX, mInternalZeroIndex);
        startActivityForResult(startNewCalibration, CALIBRATION_REQUEST_CODE);
    }

    // Request code for opening the calibration data
    private static final int OPEN_FILE = 2;

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void openFile(){
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        startActivityForResult(intent, OPEN_FILE);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void loadCalibration(View view) {
        openFile();
    }
}
