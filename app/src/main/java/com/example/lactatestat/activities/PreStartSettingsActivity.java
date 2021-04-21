package com.example.lactatestat.activities;

// Copyright (c) 2021 Emil Ekelund

import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.lactatestat.R;

import java.nio.charset.CharsetDecoder;
import java.util.BitSet;
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

    public void startSession(View view) {
        Intent startSession = new Intent(this, SessionActivity.class);
        startSession.putExtra(SELECTED_DEVICE, mSelectedDevice);
        startSession.putExtra(BIAS_VOLTAGE_INDEX, mBiasVoltageIndex);
        startSession.putExtra(BIAS_POLARITY_INDEX, mBiasPolarityIndex);
        startSession.putExtra(TIA_GAIN_INDEX, mTiaGainIndex);
        startSession.putExtra(LOAD_RESISTOR_INDEX, mLoadResistorIndex);
        startSession.putExtra(INTERNAL_ZERO_INDEX, mInternalZeroIndex);
        startActivity(startSession);
    }
}
