package com.example.lactatestat.utilities;

// Copyright (c) 2021 Emil Ekelund

public class BitConverter {

    public static int bytesToInt(byte[] bytes) {
        return (bytes[3] & 0xFF) << 24 | (bytes[2] & 0xFF) << 16 | (bytes[1] & 0xFF) << 8 | (bytes[0] & 0xFF);
    }

}