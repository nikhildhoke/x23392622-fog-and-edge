import random
import time
import json
import matplotlib.pyplot as plt
from azure.iot.device import IoTHubDeviceClient, Message

# ----------------------------
# Azure IoT Hub device string
# ----------------------------
CONNECTION_STRING = (
    "HostName=HealthIoTHub2025.azure-devices.net;"
    "DeviceId=healthsensor01;"
    "SharedAccessKey=YY7tZ4l3F0j1l58aMhYROH8MDNs+dbtF2Tuzg/FtUk8="
)

# ----------------------------
# Sensor definitions
# ----------------------------
SENSORS = [
    {"name": "ECG", "type": "ecg", "unit": "mV_rms",
     "normal_range": (0.5, 2.0), "critical_threshold_high": 3.0,
     "critical_threshold_low": None, "data_frequency": 20, "power_usage": 0.05},
    {"name": "HeartRate", "type": "bpm", "unit": "bpm",
     "normal_range": (60, 100), "critical_threshold_high": 120,
     "critical_threshold_low": 40, "data_frequency": 30, "power_usage": 0.04},
    {"name": "BloodPressure", "type": "blood_pressure", "unit": "mmHg",
     "normal_range": (110, 130), "critical_threshold_high": 140,
     "critical_threshold_low": 90, "data_frequency": 15, "power_usage": 0.06},
    {"name": "Oximeter", "type": "spo2", "unit": "%",
     "normal_range": (95, 100), "critical_threshold_high": None,
     "critical_threshold_low": 92, "data_frequency": 20, "power_usage": 0.03},
    {"name": "Temperature", "type": "body_temp", "unit": "°C",
     "normal_range": (36.1, 37.5), "critical_threshold_high": 38.0,
     "critical_threshold_low": 35.0, "data_frequency": 20, "power_usage": 0.03}
]

# Tracking data
latency_records = {s["name"]: [] for s in SENSORS}
transmission_counts = {s["name"]: 0 for s in SENSORS}

client = IoTHubDeviceClient.create_from_connection_string(CONNECTION_STRING)


def _utc_now_iso():
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def generate_data(sensor):
    lo, hi = sensor["normal_range"]
    if sensor["name"] == "BloodPressure":
        sys = random.uniform(105, 145)
        dia = random.uniform(65, 95)
        if random.random() < 0.05: sys += random.uniform(10, 35)
        if random.random() < 0.05: dia += random.uniform(5, 25)
        alert = sys >= 140 or dia >= 90
        value = {"systolic": round(sys, 1), "diastolic": round(dia, 1)}
    elif sensor["name"] == "ECG":
        val = random.uniform(lo, hi)
        if random.random() < 0.05: val = sensor["critical_threshold_high"] + 0.5
        alert = val >= sensor["critical_threshold_high"]
        value = round(val, 3)
    elif sensor["name"] == "HeartRate":
        val = random.uniform(lo, hi)
        if random.random() < 0.05:
            val = random.choice([random.uniform(125, 160), random.uniform(30, 45)])
        alert = (sensor["critical_threshold_high"] and val >= sensor["critical_threshold_high"]) or \
                (sensor["critical_threshold_low"] and val <= sensor["critical_threshold_low"])
        value = round(val, 1)
    elif sensor["name"] == "Oximeter":
        val = random.uniform(94, 99)
        if random.random() < 0.05: val = random.uniform(85, 91)
        alert = val <= sensor["critical_threshold_low"]
        value = round(val, 1)
    elif sensor["name"] == "Temperature":
        val = random.uniform(lo, hi)
        if random.random() < 0.05: val = random.choice([random.uniform(38.0, 39.5), random.uniform(34.0, 35.0)])
        alert = (sensor["critical_threshold_high"] and val >= sensor["critical_threshold_high"]) or \
                (sensor["critical_threshold_low"] and val <= sensor["critical_threshold_low"])
        value = round(val, 2)
    else:
        val = random.uniform(lo, hi)
        alert = False
        value = round(val, 2)

    return {
        "sensor_name": sensor["name"],
        "sensor_type": sensor["type"],
        "value": value,
        "unit": sensor["unit"],
        "timestamp": _utc_now_iso(),
        "is_alert": alert
    }


def send_to_hub(data):
    start = time.time()
    msg = Message(json.dumps(data))
    msg.content_encoding = "utf-8"
    msg.content_type = "application/json"
    if data["is_alert"]:
        msg.custom_properties["alert"] = "true"
        msg.custom_properties["priority"] = "high"
    client.send_message(msg)
    latency = time.time() - start
    nm = data["sensor_name"]
    latency_records[nm].append(latency)
    transmission_counts[nm] += 1
    print(f"[SENT] {nm:12s} | Value: {data['value']} {data['unit']} | "
          f"Alert: {data['is_alert']} | Latency: {latency:.4f}s")


def calculate_actual_power(sim_time):
    stats = {}
    seconds_per_month = 60 * 60 * 24 * 30
    months_sim = sim_time / seconds_per_month
    for s in SENSORS:
        count = transmission_counts[s["name"]]
        stats[s["name"]] = (count * s["power_usage"] / months_sim) / 1000 if months_sim > 0 else 0
    return stats


def plot_graphs(power_stats):
    # Avg latency
    avg_latency = {name: (sum(times) / len(times) if times else 0)
                   for name, times in latency_records.items()}

    plt.figure(figsize=(12, 4))

    plt.subplot(1, 3, 1)
    plt.bar(avg_latency.keys(), avg_latency.values(), color='orange')
    plt.title("Average Latency per Sensor (s)")
    plt.xticks(rotation=45)

    plt.subplot(1, 3, 2)
    plt.bar(power_stats.keys(), power_stats.values(), color='green')
    plt.title("Actual Power Usage (kWh)")
    plt.xticks(rotation=45)

    plt.subplot(1, 3, 3)
    plt.bar(transmission_counts.keys(), transmission_counts.values(), color='blue')
    plt.title("Transmissions per Sensor")
    plt.xticks(rotation=45)

    plt.tight_layout()
    plt.show()


def main():
    start_time = time.time()
    try:
        while True:
            for s in SENSORS:
                send_to_hub(generate_data(s))
            time.sleep(1)
    except KeyboardInterrupt:
        sim_time = time.time() - start_time
        print("\nSimulation Ended")
        power_stats = calculate_actual_power(sim_time)

        # Save results to JSON
        results = {
            "latency_records": latency_records,
            "transmission_counts": transmission_counts,
            "power_stats": power_stats
        }
        with open("health_simulation_results.json", "w") as f:
            json.dump(results, f, indent=4)

        print(f"Results saved to health_simulation_results.json")
        plot_graphs(power_stats)
        client.shutdown()


if __name__ == "__main__":
    main()
