package com.example.bluetoothserial;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Locale;

public class DisplayActivity extends AppCompatActivity {
    public TextView busStatusText;
    private boolean isBusStopped = false;

    public static boolean isActive = false;
    public static DisplayActivity instance;
    private static int lastValidLap = 0;

    // Fields for tracking lap changes.
    private long lapStartTime = 0;
    private int previousLap = -1;

    // Field to store the initial number of stops left at launch for slider progress.
    private int initialStopsLeft = 0;

    public TextView speedText, timeText, stopsText, lapText, userStationText, timeToUserStopText;
    public SeekBar busProgressSeekBar;

    // Countdown fields for the time display.
    private final Handler countdownHandler = new Handler();
    public Runnable countdownRunnable;
    private long remainingMillis;
    private long fixedCountdownMillis = 0;
    private long countdownStartTime = 0;

    // Constants
    public static final int AVG_TIME_BETWEEN_STATIONS = 36;  // fallback value (seconds)
    private static final int TOTAL_STATIONS = 10;
    // Suppose the distance between stops is 1000m (adjust as needed)
    private static final double DISTANCE_BETWEEN_STOPS = 1000.0;
    private final String CHANNEL_ID = "bus_notification_channel";
    private final int NOTIFICATION_ID = 101;
    private boolean oneStopNotificationSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display);

        isActive = true;
        instance = this;

        // Binding the  UI components to the class components
        speedText = findViewById(R.id.speedText);
        stopsText = findViewById(R.id.stopsText);
        lapText = findViewById(R.id.lapText);
        userStationText = findViewById(R.id.userStationText);
        timeToUserStopText = findViewById(R.id.timeToUserStopText);
        busProgressSeekBar = findViewById(R.id.busProgressSeekBar);
        busStatusText = findViewById(R.id.busStatusText);

        // Retrieve intent data.
        Intent intent = getIntent();
        String speed = intent.getStringExtra("SPEED");           // e.g., "60"
        String timeLeft = intent.getStringExtra("TIME_LEFT");
        String lap = intent.getStringExtra("BUS_STATION");
        String userStation = intent.getStringExtra("USER_STATION");

        // Update UI.
        speedText.setText("Speed: " + speed + " km/h");
        userStationText.setText("Your Station: " + userStation);

        int lapCount = 0;
        try {
            lapCount = Integer.parseInt(lap);
        } catch (NumberFormatException e) {
            Log.e("DisplayActivity", "Error parsing lap: " + lap, e);
        }
        if (lapCount < 0) {
            lapCount = lastValidLap;
        } else {
            lastValidLap = lapCount;
        }
        int computedBusStation = lapCount + 1;
        lapText.setText("Bus Station: " + computedBusStation);

        int userStationVal = 0;
        try {
            userStationVal = Integer.parseInt(userStation.replaceAll("[^0-9]", ""));//0-9 means excluding everything but 0-9 values
        } catch (NumberFormatException e) {
            Log.e("DisplayActivity", "Error parsing userStation: " + userStation, e);
        }
        int stopsLeft;
        if (userStationVal >= computedBusStation) {
            stopsLeft = userStationVal - computedBusStation;
        } else {
            stopsLeft = (TOTAL_STATIONS - computedBusStation) + userStationVal;
        }
        stopsText.setText("Stops Left: " + stopsLeft);
        initialStopsLeft = stopsLeft; // Save initial stops for slider progress.

        // --- Set up the countdown based on live time + approximations ---
        long liveMillis = parseTimeToMillis(timeLeft); // Live time from ESP32.
        // Calculate an approximation for one stop based on current speed.
        double currentSpeed = 0;
        try {
            currentSpeed = Double.parseDouble(speed); // km/h
        } catch (NumberFormatException e) {
            currentSpeed = 60; // fallback value
        }
        long approxMillis = 60000; // default 60 sec.
        if (currentSpeed > 0) {
            double speedMps = currentSpeed * 1000.0 / 3600.0;
            approxMillis = (long) Math.round((DISTANCE_BETWEEN_STOPS / speedMps) * 1000);
        }
        // Total remaining time = live time for current lap + (stopsLeft - 1) approximations.
        long newTotalMillis = liveMillis;
        if (stopsLeft > 0) {
            newTotalMillis += (stopsLeft - 1) * approxMillis;
        }
        fixedCountdownMillis = newTotalMillis;
        countdownStartTime = System.currentTimeMillis();
        remainingMillis = newTotalMillis;
        timeToUserStopText.setText("Time Before Your Stop: " + formatMillis(newTotalMillis));

        busProgressSeekBar.setMax(100);
        busProgressSeekBar.setProgress(0);

        startCountdown();
        createNotificationChannel();

        Button btnViewStats = findViewById(R.id.btnViewStats);
        btnViewStats.setOnClickListener(v -> {
            Intent statsIntent = new Intent(DisplayActivity.this, Statistics.class);
            startActivity(statsIntent);
        });

        // Initialize lap timer.
        lapStartTime = System.currentTimeMillis();
    }

    // Countdown runnable runs every second (updates text display, etc.).
    private void startCountdown() {
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - countdownStartTime;
                remainingMillis = fixedCountdownMillis - elapsed;
                if (remainingMillis < 0) {
                    remainingMillis = 0;
                }
                updateTimeDisplay();
                try {
                    int busStationVal = Integer.parseInt(lapText.getText().toString().replaceAll("[^0-9]", ""));
                    int userStationVal = Integer.parseInt(userStationText.getText().toString().replaceAll("[^0-9]", ""));
                    int diff = userStationVal - busStationVal;
                    Log.d("DisplayActivity", "Countdown diff: " + diff);
                    // Fire notification when 1 stop remains.
                    if (diff == 1 && !oneStopNotificationSent) {
                        instance.sendNotification("Stop Notification", "Your stop is only 1 stop away.");
                        oneStopNotificationSent = true;
                    }
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
                countdownHandler.postDelayed(this, 1000);
            }
        };
        countdownHandler.postDelayed(countdownRunnable, 1000);
    }

    private void updateTimeDisplay() {
        String speedString = speedText.getText().toString().replaceAll("[^0-9.]", ""); // Extract numeric part
        double currentSpeed = 5; // default fallback

        try {
            currentSpeed = Double.parseDouble(speedString);
        } catch (NumberFormatException e) {
            Log.e("DisplayActivity", "Error parsing speed for display update", e);
        }

        // Treat speeds below 5 km/h as stopped
        if (currentSpeed < 5) {
            currentSpeed = 0.0;
            busStatusText.setText("Bus Status: Bus Has Stopped");
            timeToUserStopText.setVisibility(timeToUserStopText.INVISIBLE); // Hides the countdown
        } else {
            busStatusText.setText("Bus Status: Moving");
            timeToUserStopText.setVisibility(timeToUserStopText.VISIBLE); // Shows the countdown
            timeToUserStopText.setText("Time Before Your Stop: " + formatMillis(remainingMillis));
        }
    }



    // Updates the slider progress.
    private void updateStopProgress(int currentStopsLeft) {
        if (initialStopsLeft > 0) {
            int progress = ((initialStopsLeft - currentStopsLeft) * 100) / initialStopsLeft;
            busProgressSeekBar.setProgress(progress);
            Log.d("DisplayActivity", "Slider progress updated: " + progress + "%");
        }
    }

    // Format milliseconds into MM:SS.
    private String formatMillis(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    // Parse time string ("mm:ss" or "ss") into milliseconds.
    public long parseTimeToMillis(String timeStr) {
        try {
            if (timeStr.contains(":")) {
                String[] parts = timeStr.split(":");
                int minutes = Integer.parseInt(parts[0].trim());
                int seconds = Integer.parseInt(parts[1].trim());
                return (minutes * 60 + seconds) * 1000L;
            } else {
                return Integer.parseInt(timeStr.trim()) * 1000L;// l is here for the millis logic as it requires larger bits
            }
        } catch (NumberFormatException e) {
            Log.e("DisplayActivity", "Error parsing time string: " + timeStr, e);
            return 0;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Bus Notifications";
            String description = "Notifications for bus stop updates";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * updateDisplayData is called (once per lap) when new data from the ESP32 is received.
     * It updates the UI components, adds the current speed to the tracker,
     * recalculates stops left and the new remaining time (peeling back the approximations),
     * resets the countdown, and then broadcasts an update with the extra "lapChanged" flag.
     */
    public static void updateDisplayData(String speed, String timeLeft, String stops, String lap, String userStation) {
        if (instance == null) {
            Log.e("DisplayActivity", "Instance is null, unable to update display data");
            return;
        }
        if (timeLeft == null || timeLeft.trim().isEmpty()) {
            Log.w("DisplayActivity", "updateDisplayData called with empty timeLeft");
            return;
        }
        instance.runOnUiThread(() -> {
            Log.d("DisplayActivity", "updateDisplayData called with speed: " + speed +
                    ", timeLeft: " + timeLeft +
                    ", stops: " + stops +
                    ", lap: " + lap +
                    ", userStation: " + userStation);

            instance.speedText.setText("Speed: " + speed + " km/h");
            instance.userStationText.setText("Your Station: " + userStation);

            // Track the speed.
            try {
                double spd = Double.parseDouble(speed);
                BusDataHolder.getInstance().addSpeed(spd);
            } catch (NumberFormatException e) {
                Log.e("DisplayActivity", "Error parsing speed: " + speed, e);
            }

            int lapCount = 0;
            try {
                lapCount = Integer.parseInt(lap);
            } catch (NumberFormatException e) {
                Log.e("DisplayActivity", "Error parsing lap: " + lap, e);
            }
            boolean lapChanged = false;
            if (lapCount < 0) {
                lapCount = lastValidLap;
            } else {
                if (lapCount != instance.previousLap && instance.previousLap != -1) {
                    lapChanged = true;
                }
                lastValidLap = lapCount;
            }
            int computedBusStation = lapCount + 1;
            instance.lapText.setText("Bus Station: " + computedBusStation);

            int userStationVal = 0;
            try {
                userStationVal = Integer.parseInt(userStation.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                Log.e("DisplayActivity", "Error parsing userStation: " + userStation, e);
            }
            int stopsLeftCalc;
            if (userStationVal >= computedBusStation) {
                stopsLeftCalc = userStationVal - computedBusStation;
            } else {
                stopsLeftCalc = (TOTAL_STATIONS - computedBusStation) + userStationVal;
            }
            instance.stopsText.setText("Stops Left: " + stopsLeftCalc);

            // Update slider progress based on stops left.
            instance.updateStopProgress(stopsLeftCalc);

            // Reset the notification flag so that a notification may fire for the new lap.
            instance.oneStopNotificationSent = false;

            // Calculate new remaining time.
            long liveMillis = instance.parseTimeToMillis(timeLeft);
            double currentSpeed;
            try {
                currentSpeed = Double.parseDouble(speed);
            } catch (NumberFormatException e) {
                currentSpeed = 60;
            }
            long approxMillis = 60000;
            if (currentSpeed > 0) {
                double speedMps = currentSpeed * 1000.0 / 3600.0;
                approxMillis = (long) Math.round((DISTANCE_BETWEEN_STOPS / speedMps) * 1000);
            }
            long newTotalMillis = liveMillis;
            if (stopsLeftCalc > 0) {
                newTotalMillis += (stopsLeftCalc - 1) * approxMillis;
            }
            instance.fixedCountdownMillis = newTotalMillis;
            instance.countdownStartTime = System.currentTimeMillis();
            instance.remainingMillis = newTotalMillis;
            instance.timeToUserStopText.setText("Time Before Your Stop: " + instance.formatMillis(newTotalMillis));

            // Process lap change logic.
            if (instance.previousLap == -1) {
                instance.previousLap = lapCount;
                instance.lapStartTime = System.currentTimeMillis();
            } else if (lapChanged) {
                long lapDurationSeconds = (System.currentTimeMillis() - instance.lapStartTime) / 1000;
                Log.d("DisplayActivity", "Lap changed from " + instance.previousLap + " to " + lapCount +
                        ". Lap duration: " + lapDurationSeconds + " sec.");
                BusDataHolder.getInstance().addTripDuration((int) lapDurationSeconds);
                instance.previousLap = lapCount;
                instance.lapStartTime = System.currentTimeMillis();
            }

            // Broadcast update for Statistics with a flag indicating whether the lap changed.
            Intent updateIntent = new Intent("BUS_DATA_UPDATED");
            updateIntent.putExtra("lapChanged", lapChanged);
            LocalBroadcastManager.getInstance(instance).sendBroadcast(updateIntent);
        });
    }

    // sendNotification is non-static;
    private void sendNotification(String title, String message) {
        Log.d("DisplayActivity", "Sending notification: " + title + " - " + message);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
        instance = null;
        countdownHandler.removeCallbacks(countdownRunnable);
    }
}