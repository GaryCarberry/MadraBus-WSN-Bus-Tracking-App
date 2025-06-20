package com.example.bluetoothserial;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Statistics extends AppCompatActivity {

    // TextViews for statistics.
    private TextView tvAverageSpeed, tvAverageLapDuration;
    // Two PieCharts: one for a speedometer gauge and one for stop performance classification.
    private PieChart speedometerChart;
    private PieChart pieChart;

    // Expected lap duration baseline (in seconds) for classification.
    private double expectedDuration = 72;
    // Maximum expected speed (km/h) for the gauge.
    private final double MAX_EXPECTED_SPEED = 80.0;

    // BroadcastReceiver that fires once per lap update.
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Retrieve the lapChanged flag; update classification PieChart only if true.
            boolean lapChanged = intent.getBooleanExtra("lapChanged", false);
            updateStats(lapChanged);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.statistic);

        // Bind views.
        tvAverageSpeed = findViewById(R.id.tvAverageSpeed);
        tvAverageLapDuration = findViewById(R.id.tvAverageLapDuration);
        pieChart = findViewById(R.id.pieChart);
        speedometerChart = findViewById(R.id.speedometerChart);

        // Initial update.
        updateStats(true);

        // Register BroadcastReceiver to update on each lap.
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(receiver, new IntentFilter("BUS_DATA_UPDATED"));
    }

    /**
     * Updates the text fields and gauge.
     * @param lapChanged if true, then update (and animate) the classification PieChart.
     */
    private void updateStats(boolean lapChanged) {
        BusDataHolder holder = BusDataHolder.getInstance();

        // Calculate average speed.
        double avgSpeed = holder.getAverageSpeed();

        // Calculate average lap duration.
        List<Integer> laps = holder.getTripDurations();
        double avgLap = 0;
        if (!laps.isEmpty()) {
            int sum = 0;
            for (int duration : laps) {
                sum += duration;
            }
            avgLap = (double) sum / laps.size();
        }

        // Update text fields.
        tvAverageSpeed.setText("Average Speed: " + String.format(Locale.getDefault(), "%.2f km/h", avgSpeed));
        tvAverageLapDuration.setText("Average Lap Duration: " + String.format(Locale.getDefault(), "%.2f sec", avgLap));

        // Always update the speedometer gauge.
        updateSpeedometerGauge(avgSpeed);

        // Update the classification PieChart only when a new lap occurs.
        if (lapChanged) {
            updatePieChart();
        }
    }

    // Updates the speedometer gauge using a PieChart.
    private void updateSpeedometerGauge(double avgSpeed) {
        double percentage = (avgSpeed / MAX_EXPECTED_SPEED) * 100;
        if (percentage > 100) percentage = 100;

        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry((float) percentage, "Speed"));
        entries.add(new PieEntry((float) (100 - percentage), ""));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(new int[]{Color.parseColor("#FF4081"), Color.LTGRAY});
        dataSet.setDrawValues(false);

        PieData data = new PieData(dataSet);
        speedometerChart.setData(data);

        speedometerChart.getDescription().setEnabled(false);
        speedometerChart.getLegend().setEnabled(false);
        // Configure gauge look: rotate to display as a half circle.
        speedometerChart.setRotationAngle(180);
        speedometerChart.setHoleRadius(60f);
        speedometerChart.setTransparentCircleRadius(65f);
        speedometerChart.setRotationEnabled(false);
        speedometerChart.setHighlightPerTapEnabled(false);
        speedometerChart.setCenterText(String.format(Locale.getDefault(), "%.2f km/h", avgSpeed));
        speedometerChart.setCenterTextColor(Color.DKGRAY);
        speedometerChart.setCenterTextSize(18f);

        speedometerChart.invalidate();
    }

    // Updates the classification PieChart.
    private void updatePieChart() {
        int thresholdMargin = 15;
        int lowerThreshold = (int) (expectedDuration - thresholdMargin);
        int upperThreshold = (int) (expectedDuration + thresholdMargin);
        Log.d("Statistics", "Expected: " + expectedDuration +
                ", Lower Threshold: " + lowerThreshold +
                ", Upper Threshold: " + upperThreshold);

        int earlyCount = 0, onTimeCount = 0, lateCount = 0;
        List<Integer> laps = BusDataHolder.getInstance().getTripDurations();
        for (int duration : laps) {
            if (duration < lowerThreshold) {
                earlyCount++;
            } else if (duration > upperThreshold) {
                lateCount++;
            } else {
                onTimeCount++;
            }
        }
        Log.d("Statistics", "Classification: Early=" + earlyCount +
                ", On Time=" + onTimeCount + ", Late=" + lateCount);

        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(earlyCount, "Early"));
        entries.add(new PieEntry(onTimeCount, "On Time"));
        entries.add(new PieEntry(lateCount, "Late"));

        PieDataSet dataSet = new PieDataSet(entries, "Stop Performance");
        dataSet.setColors(new int[]{Color.MAGENTA, Color.GREEN, Color.RED});
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(14f);

        PieData pieData = new PieData(dataSet);
        pieChart.setData(pieData);
        pieChart.setCenterText("Bus Arrivals");
        pieChart.setCenterTextColor(Color.DKGRAY);
        pieChart.getDescription().setEnabled(false);
        pieChart.setEntryLabelColor(Color.BLACK);
        pieChart.setEntryLabelTextSize(12f);
        pieChart.animateY(1000);
        pieChart.invalidate();
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onDestroy();
    }
}
