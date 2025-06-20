# MadraBus-WSN-Bus-Tracking-App
Overview
The Madrabus Tracking System is a real-time bus tracking solution designed to improve user experience in Ireland’s often unreliable public transport system. The project addresses the common frustration of waiting endlessly for buses without knowing their exact location or estimated arrival time.

The system consists of a custom Android app paired with an ESP-32 microcontroller acting as a server. Together, they track bus speed, position, and stops to provide users with accurate, up-to-date information on bus arrival times.

Key Features
Real-time bus location and speed tracking: Uses an ESP-32 chip with Bluetooth communication to send live data to the mobile app.

User-friendly Android app: Built with Java, Kotlin, and Groovy DSL to receive, parse, and display bus tracking data.

Interactive touchscreen slider: ESP-32 integrates with a TFT display and touch controller for local simulation control and feedback.

Robust Bluetooth connectivity: Implements fault tolerance with automatic reconnection to maintain seamless data flow.

Efficient data processing: Utilizes multi-threading and buffered readers for smooth and reliable data parsing on the client side.

Fault tolerance: Both server and app include mechanisms to handle lost connections and data parsing errors gracefully.

Visual statistics: Generates graphical data insights (e.g., pie charts showing bus punctuality) for enhanced user understanding.

Technical Highlights
Server Side (ESP-32)
Replaced an initial multi-device setup (Raspberry Pi + Arduino) with a streamlined ESP-32 solution combining Wi-Fi, Bluetooth, and MQTT capabilities.

Programmed in C++ with Arduino libraries: Bluetooth Serial, TFT_ESPI, and XPT2046 touchscreen.

Developed interactive GUI components like a slider on the TFT display for controlling simulation parameters.

Implemented core functions such as:

calculateAndDisplayLapTime() to estimate remaining travel time based on speed.

updateSerialCountdown() to manage bus stop countdown logic and data output.

Utilized Bluetooth serial for low-latency, low-power data transmission suitable for short-range mobile connectivity.

Client Side (Android App)
Developed the app in Java with Kotlin compilation, focusing on:

Bluetooth device discovery, pairing, and connection handling.

Multi-threaded data reading and parsing from the ESP-32 data stream.

UI updates reflecting real-time speed, lap, and stop information.

Notification system alerting users when the bus is one or zero stops away.

Employed efficient data filtering and parsing to minimize errors from continuous data streams.

Designed the Display Activity screen to show clear, user-friendly real-time information.

Challenges & Learnings
Managing hardware constraints and adapting code to different components (ESP-32 board and touchscreen).

Handling multi-threading in Android to maintain a responsive UI alongside a persistent Bluetooth connection.

Debugging complex parsing issues and developing fault tolerance to maintain app stability.

Learning and integrating multiple technologies: Bluetooth communication, embedded device programming, Android development, and UI/UX design.

Collaboration and resourcefulness using community documentation, GitHub repositories, and AI assistance.

Future Improvements
Implement autonomous bus speed simulation on the ESP-32 to better reflect real-world speed fluctuations.

Refine time estimation logic on the app by incorporating average bus speed for more accurate arrival predictions.

Improve data parsing robustness by regulating data transmission intervals or enhancing parsing algorithms.

Conclusion
The Madrabus Tracking System provided valuable experience in embedded systems, real-time data processing, and full-stack application development. It demonstrates proficiency in hardware-software integration, Bluetooth communication, and Android app development — skills highly relevant to roles in IoT, mobile software, and systems engineering.

