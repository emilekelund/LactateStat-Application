package com.example.lactatestat.services;

// Copyright (c) 2021 Emil Ekelund

import java.util.UUID;

public class LactateStatUUIDs {

    public static UUID LACTATESTAT_SERVICE =
            UUID.fromString("0ee94b6a-af10-433b-b396-af3aecda5508");

    public static UUID LACTATESTAT_MEASUREMENT =
            UUID.fromString("0ee9bffe-af10-433b-b396-af3aecda5508");

    public static UUID LACTATESTAT_SETTINGS =
            UUID.fromString("0ee9bfab-af10-433b-b396-af3aecda5508");


    // UUID for the client characteristic which is necessary for notifications
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
}