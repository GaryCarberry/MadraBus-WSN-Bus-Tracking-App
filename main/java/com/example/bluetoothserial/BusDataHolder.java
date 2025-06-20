package com.example.bluetoothserial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BusDataHolder {
    private static BusDataHolder instance;
    private final List<Integer> tripDurations;
    private final List<Double> speedValues;
    private int notificationCount = 0;

    private BusDataHolder() {
        tripDurations = new ArrayList<>();
        speedValues = new ArrayList<>();
    }

    public static synchronized BusDataHolder getInstance() {
        if (instance == null) {
            instance = new BusDataHolder();
        }
        return instance;
    }

    public void addTripDuration(int duration) {
        tripDurations.add(duration);
    }

    public List<Integer> getTripDurations() {
        return new ArrayList<>(tripDurations);
    }

    public void addSpeed(double speed) {
        speedValues.add(speed);
    }

    public List<Double> getSpeedValues() {
        return new ArrayList<>(speedValues);
    }

    public double getAverageSpeed() {
        if (speedValues.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Double s : speedValues) {
            sum += s;
        }
        return sum / speedValues.size();
    }

    public double getMaxSpeed() {
        if (speedValues.isEmpty()) {
            return 0;
        }
        return Collections.max(speedValues);
    }

    public double getMinSpeed() {
        if (speedValues.isEmpty()) {
            return 0;
        }
        return Collections.min(speedValues);
    }

    public void incrementNotificationCount() {
        notificationCount++;
    }

    public int getNotificationCount() {
        return notificationCount;
    }
}
