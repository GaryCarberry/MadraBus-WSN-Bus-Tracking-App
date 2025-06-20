// The following code has been repackaged an repurposed from a previous f1 simulation project that reacted to wear and tear of tyres as well as 
// How speed can have an effect, Some terms may differ from expected, eg laps instead of stops, But i would like to highlight the connections
// and similarities between both projects as well as where and how i reused some assests of my previous project




#include <TFT_eSPI.h>
#include <TFT_eWidget.h>
#include <XPT2046_Touchscreen.h>
#include "BluetoothSerial.h"  //Bluetooth Serial as its a constat flow that i can buffer to receive live info

// Touchscreen pins
#define XPT2046_IRQ 36
#define XPT2046_MOSI 32
#define XPT2046_MISO 39
#define XPT2046_CLK 25
#define XPT2046_CS 33

// Screen dimensions
#define SCREEN_WIDTH 320
#define SCREEN_HEIGHT 240

// TFT and touchscreen setup
TFT_eSPI tft = TFT_eSPI();
TFT_eSprite knob = TFT_eSprite(&tft);
XPT2046_Touchscreen touchscreen(XPT2046_CS, XPT2046_IRQ);
SliderWidget s1 = SliderWidget(&tft, &knob);

// Variables that make up the traits and performance of the bus, these can be chanegd to tailor known features of a bus
int maxSpeed = 120; // Very high for bug testing and simulation purposes
float track = 1;    
int laps = 10; // In this case laps are bus stops     
int lapCounter = 0; 
float minLapTime;  
float lapTimeSeconds; 
unsigned long lapStartTime = 0; 
bool lapStarted = false;
int currentSpeed = 0; 

// Bluetooth Serial object
BluetoothSerial SerialBT;  

void setup() {
 //Touchscreen initialisation
  SPI.begin(XPT2046_CLK, XPT2046_MISO, XPT2046_MOSI, XPT2046_CS);
  touchscreen.begin(SPI);
  touchscreen.setRotation(1);

  // Initialise the tft display as well so that i an try touchscreen again
  tft.begin();
  tft.setRotation(1);
  tft.fillScreen(TFT_BLACK);

  // Configure what the slider looks like 
  slider_t param;
  param.slotWidth = 9;
  param.slotLength = 200;
  param.slotColor = TFT_BLUE;
  param.slotBgColor = TFT_BLACK;
  param.orientation = H_SLIDER;
  param.knobWidth = 15;
  param.knobHeight = 25;
  param.knobRadius = 5;
  param.knobColor = TFT_WHITE;
  param.knobLineColor = TFT_RED;
  param.sliderLT = 0;
  param.sliderRB = 100;
  param.startPosition = 50;

  s1.drawSlider(60, SCREEN_HEIGHT / 2, param);

  // Setup Serial communication with a baudrate of 115200 so i know what the bluetooth protocol is sending and can debug
  Serial.begin(115200);

  // Initialize Bluetooth Serial
  SerialBT.begin("ESP32-RaceSim");  // Set Bluetooth name

  // Wait for the Bluetooth connection
  while (!SerialBT.hasClient()) {
    delay(1000);  // Wait until the phone connects to ESP32
    Serial.println("Waiting for Bluetooth client...");
  }
  
  Serial.println("Bluetooth client connected");

  // Calculate the minimum lap time at max speed
  minLapTime = (track * 3600) / maxSpeed;
  lapStartTime = millis();
}

void loop() {
  // Check Bluetooth connection status
  if (!SerialBT.hasClient()) {
    Serial.println("Bluetooth client disconnected. Waiting for a new connection...");
    while (!SerialBT.hasClient()) {
      delay(1000);  // Wait for reconnection
    }
    Serial.println("Bluetooth client reconnected");
  }

  // GUI slider control
  if (touchscreen.tirqTouched() && touchscreen.touched()) {
    TS_Point p = touchscreen.getPoint();
    int x = map(p.x, 200, 3700, 0, SCREEN_WIDTH);
    int y = map(p.y, 240, 3800, 0, SCREEN_HEIGHT);

    if (s1.checkTouch(x, y)) {
      int sliderValue = map(x, 60, 260, 0, 100);
      currentSpeed = map(sliderValue, 0, 100, 0, maxSpeed); 

      s1.setSliderPosition(sliderValue);

      tft.fillRect(60, SCREEN_HEIGHT / 2 + 40, 200, 30, TFT_BLACK); 
      tft.setTextColor(TFT_WHITE, TFT_BLACK);
      tft.setTextSize(2);
      tft.setCursor(60, SCREEN_HEIGHT / 2 + 40);
      tft.printf("Speed: %d km/h", currentSpeed);

      calculateAndDisplayLapTime();
    }
  }

  // Continuously update countdown and send via Bluetooth
  updateSerialCountdown();
}

void calculateAndDisplayLapTime() {
  if (currentSpeed > 0) {
    lapTimeSeconds = (track * 3600) / currentSpeed;
    lapStarted = true;
  }
}

void updateSerialCountdown() {
  static unsigned long lastUpdateTime = 0;
  unsigned long currentMillis = millis();

  if (lapStarted) {
    float elapsedSeconds = (currentMillis - lapStartTime) / 1000.0;
    float remainingTime = lapTimeSeconds - elapsedSeconds;

    if (remainingTime <= 0) {
      lapCounter++;
      
      // Wrap-around logic for laps
      if (lapCounter > laps) {
        lapCounter = 1;  // Reset lap to 1 when it exceeds total laps
      }

      lapStartTime = millis();
      lapStarted = false;

      remainingTime = lapTimeSeconds;
      lapStarted = true;
    }

    if (currentMillis - lastUpdateTime >= 1000) {
      lastUpdateTime = currentMillis;
      String message = "Lap: " + String(lapCounter) + "/" + String(laps) +
                       ", Time Left: " + String(remainingTime, 1) + " sec, Speed: " + String(currentSpeed) + " km/h";
      
      Serial.println(message); 
      SerialBT.println(message); // Send data over Bluetooth
    }
  }
}
