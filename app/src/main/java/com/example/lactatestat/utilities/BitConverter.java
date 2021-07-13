package com.example.lactatestat.utilities;

// Copyright (c) 2021 Emil Ekelund

public class BitConverter {

    public static int bytesToInt(byte[] bytes) {
        return (bytes[3] & 0xFF) << 24 | (bytes[2] & 0xFF) << 16 | (bytes[1] & 0xFF) << 8 | (bytes[0] & 0xFF);
    }

    public static int[] bytesToIntArray(byte[] bytes) {
        int[] combinedValues = new int[3];
        combinedValues[0] = (bytes[7] & 0xFF) << 24 | (bytes[6] & 0xFF) << 16 | (bytes[5] << 8) | (bytes[4] & 0xFF);
        combinedValues[1] = (bytes[3] & 0xFF) << 8 | (bytes[2] & 0xFF);
        combinedValues[2] = (bytes[1] & 0xFF) << 8 | (bytes[0] & 0xFF);
        return combinedValues;
    }

}