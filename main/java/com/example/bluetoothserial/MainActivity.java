// MainActivity.java
package com.example.bluetoothserial;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 1;
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final int TOTAL_STATIONS = 10;
    private static final int AVG_TIME_BETWEEN_STATIONS = 36;

    private BluetoothAdapter bluetoothAdapter;
    private ArrayList<BluetoothDevice> deviceList = new ArrayList<>();
    private BluetoothSocket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Handler handler = new Handler();

    private ListView listView;
    private Spinner stationSpinner;
    private Button connectButton;
    private Button requestDataButton;

    private int lastValidSpeed = -1;
    private int lastValidLap = 0;
    private boolean notificationSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        createNotificationChannel();

        listView = findViewById(R.id.deviceListView);
        stationSpinner = findViewById(R.id.stationSpinner);
        connectButton = findViewById(R.id.connectButton);
        requestDataButton = findViewById(R.id.requestDataButton);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBluetoothPermissions();

        ArrayAdapter<String> stationAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, getStationList());
        stationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        stationSpinner.setAdapter(stationAdapter);

        connectButton.setOnClickListener(v -> listPairedDevices());
        requestDataButton.setOnClickListener(v -> requestData());
        requestDataButton.setEnabled(false);
    }

    private void listPairedDevices() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED)
            return;
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<String> names = new ArrayList<>();
        deviceList.clear();
        for (BluetoothDevice device : pairedDevices) {
            names.add(device.getName() + "\n" + device.getAddress());
            deviceList.add(device);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                connectToDevice(deviceList.get(position)));
    }

    private void connectToDevice(BluetoothDevice device) {
        ProgressDialog dialog = ProgressDialog.show(this, "Connecting", "Please wait...", true);
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED)
                    return;
                socket = device.createRfcommSocketToServiceRecord(BT_UUID);
                socket.connect();
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();
                    requestDataButton.setEnabled(true);
                    dialog.dismiss();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
                e.printStackTrace();
            }
        }).start();
    }

    private void requestData() {
        if (outputStream != null) {
            try {
                outputStream.write("GET_DATA\n".getBytes());
                readDataFromESP32();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to request data", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void readDataFromESP32() {
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d("BluetoothData", "Received: " + line);

                    if (line.trim().isEmpty() || !line.contains("Lap") || !line.contains("Speed") || !line.contains("Time Left")) {
                        continue;
                    }

                    try {
                        String[] parts = line.split(", ");
                        if (parts.length < 3) continue;

                        // Parse lap info
                        String lapInfo = parts[0].split(" ")[1];
                        int currentLap = Integer.parseInt(lapInfo.split("/")[0]);
                        lastValidLap = currentLap;

                        // Parse raw time left
                        String timeLeftRaw = parts[1].split(" ")[2];
                        int parsedTimeLeft = (int) Float.parseFloat(timeLeftRaw);

                        // Parse raw speed
                        int rawSpeed = Integer.parseInt(parts[2].split(" ")[1]);
                        boolean isStopped = rawSpeed == 0;
                        int displaySpeed = (rawSpeed > 0) ? rawSpeed : lastValidSpeed;
                        if (rawSpeed > 0) lastValidSpeed = rawSpeed;

                        // Compute stops left
                        int computedBusStation = currentLap + 1;
                        int userStation = Integer.parseInt(
                                stationSpinner.getSelectedItem().toString().replaceAll("[^0-9]", ""));
                        int stopsLeft = (userStation >= computedBusStation)
                                ? userStation - computedBusStation
                                : (TOTAL_STATIONS - computedBusStation) + userStation;

                        // Determine final time left
                        String finalTimeLeft;
                        if (isStopped) {
                            finalTimeLeft = "Bus Stopped";
                        } else {
                            int estimatedTime = stopsLeft * AVG_TIME_BETWEEN_STATIONS;
                            finalTimeLeft = (parsedTimeLeft == 0)
                                    ? String.valueOf(estimatedTime)
                                    : String.valueOf(parsedTimeLeft + estimatedTime);
                        }

                        // Update UI or DisplayActivity
                        runOnUiThread(() -> {
                            if (!DisplayActivity.isActive) {
                                startDisplayActivity(
                                        String.valueOf(displaySpeed),
                                        finalTimeLeft,
                                        String.valueOf(stopsLeft),
                                        String.valueOf(currentLap),
                                        String.valueOf(userStation)
                                );
                            } else {
                                DisplayActivity.updateDisplayData(
                                        String.valueOf(displaySpeed),
                                        finalTimeLeft,
                                        String.valueOf(stopsLeft),
                                        String.valueOf(currentLap),
                                        String.valueOf(userStation)
                                );
                            }

                            if (stopsLeft == 0 && !notificationSent) {
                                sendNotification("Your selected stop is next!");
                                notificationSent = true;
                            } else if (stopsLeft != 0) {
                                notificationSent = false;
                            }
                        });

                    } catch (Exception e) {
                        Log.e("ParseError", "Error parsing data: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private ArrayList<String> getStationList() {
        ArrayList<String> stations = new ArrayList<>();
        for (int i = 1; i <= TOTAL_STATIONS; i++) {
            stations.add("Station " + i);
        }
        return stations;
    }

    private void checkBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startDisplayActivity(String speed, String timeLeft, String stops, String lap, String userStation) {
        Intent intent = new Intent(MainActivity.this, DisplayActivity.class);
        intent.putExtra("SPEED", speed);
        intent.putExtra("TIME_LEFT", timeLeft);
        intent.putExtra("STOPS", stops);
        intent.putExtra("BUS_STATION", lap);
        intent.putExtra("USER_STATION", userStation);
        startActivity(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Bus Channel";
            String description = "Notifications for bus stop alerts";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel("bus_channel", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void sendNotification(String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "bus_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Bus Arrival Alert")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, builder.build());
    }
}
