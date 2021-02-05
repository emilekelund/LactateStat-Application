package com.example.lactatestat.activities;

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

import java.util.BitSet;
import java.util.Objects;

import static com.example.lactatestat.utilities.MessageUtils.createDialog;

public class PreStartSettingsActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    private static final String TAG = PreStartSettingsActivity.class.getSimpleName();
    private static final String SELECTED_DEVICE = "selectedDevice";
    private static final String BIAS_VOLTAGE_INDEX = "biasVoltageIndex";
    private static final String BIAS_POLARITY_INDEX = "biasPolarityIndex";

    private BluetoothDevice mSelectedDevice = null;
    private String mDeviceAddress;
    private Spinner mBiasVoltageSpinner;
    private Spinner mBiasPolaritySpinner;
    private int mBiasVoltageIndex = 2;
    private int mBiasPolarityIndex = 1;

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

        mBiasVoltageSpinner.setSelection(6);
        mBiasPolaritySpinner.setSelection(1);

        final Intent intent = getIntent();
        mSelectedDevice = intent.getParcelableExtra(SELECTED_DEVICE);

        if (mSelectedDevice == null) {
            Dialog alert = createDialog("Error", "No LactateStat board connected", this);
            alert.show();
        } else {
            mDeviceAddress = mSelectedDevice.getAddress();
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        mBiasVoltageIndex = mBiasVoltageSpinner.getSelectedItemPosition();
        mBiasPolarityIndex = mBiasPolaritySpinner.getSelectedItemPosition();
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
        startActivity(startSession);
    }
}
