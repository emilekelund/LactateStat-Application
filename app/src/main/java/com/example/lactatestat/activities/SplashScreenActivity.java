package com.example.lactatestat.activities;

// Copyright (c) 2021 Emil Ekelund

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

public class SplashScreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, DashboardActivity.class);
        startActivity(intent);
        finish();
    }
}