package com.example.lactatestat.services;

// Copyright (c) 2021 Emil Ekelund

public class GattActions {
    /**
     * The action corresponding to LactateStat events from BleService.
     * Intended for IntentFilters for a BroadcastReceiver.
     */
    public final static String ACTION_GATT_LACTATESTAT_EVENT =
            "com.example.lactatestat.services.ACTION_GATT_LACTATESTAT_EVENT";

    /**
     * A flag for event info in intents (via intent.putExtra)
     */
    public final static String EVENT =
            "com.example.lactatestat.services.EVENT";

    /**
     * A flag for LactateStat data in intent (via intent.putExtra)
     */
    public final static String LACTATESTAT_DATA =
            "com.example.lactatestat.services.LACTATESTAT_DATA";


    /**
     * Events corresponding to Gatt status/events
     */
    public enum Event {
        GATT_CONNECTED("Connected"),
        GATT_DISCONNECTED("Disconnected"),
        GATT_SERVICES_DISCOVERED("Services discovered"),
        LACTATESTAT_SERVICE_DISCOVERED("LactateStat Service"),
        LACTATESTAT_SERVICE_NOT_AVAILABLE("LactateStat service unavailable"),
        DATA_AVAILABLE("Data available");

        @Override
        public String toString() {
            return text;
        }

        private final String text;

        Event(String text) {
            this.text = text;
        }
    }
}
