This project implements a three-layer hybrid Fog–Edge–Cloud architecture for real-time healthcare monitoring, inspired by the paper:
"A hybrid fog‑edge computing architecture for real‑time health monitoring in IoMT systems with optimised latency and threat resilience” (Islam et al. 2025, Scientific Reports)"
Our system simulates ECG, Heart Rate, Blood Pressure, Oximeter, and Temperature sensors in Python, transmits telemetry to Azure IoT Hub, and evaluates latency and energy consumption trade-offs between Fog-based and Cloud-based processing using Java iFogSim.

The project includes:
- Java iFogSim Simulation – Fog and Cloud infrastructure, latency & energy evaluation.
- Python Sensor Simulation – Sends synthetic healthcare telemetry to Azure IoT Hub.
- Graphical Visualisation – Bar charts plotted using Matplotlib and JFreeChart.

Architecture Layers:
- IoT Device Layer (Edge) – Python simulation of healthcare sensors.
- Fog Layer – iFogSim-based localised computation for critical data.
- Cloud Layer – Azure IoT Hub for data aggregation, analytics, and visualisation.

Technologies Used
1. Java – Core simulation logic for Fog & Cloud environments.
2. iFogSim – Simulates latency, tuple routing, and energy consumption.
3. Python – Sensor data generation & Azure IoT Hub integration.
4. Azure IoT Hub – Secure cloud IoT broker for telemetry.
5. Matplotlib – Python-based result plotting.
6. JFreeChart – Java-based chart visualisation.
