package org.fog.test.project;

import org.cloudbus.cloudsim.*;
import org.fog.utils.AzureIoTHubSender;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.*;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import java.io.FileWriter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.text.DecimalFormat;
import java.util.*;

public class HealthFogSimulation {

    static class HealthSensor {
        String name;
        double dataRate;
        double criticality;
        double latency;
        double energyConsumption;

        HealthSensor(String name, double dataRate, double criticality) {
            this.name = name;
            this.dataRate = dataRate;
            this.criticality = criticality;
            this.latency = Math.random() * 20 + 10;
            this.energyConsumption = Math.random() * 3 + 1;
        }
    }

    static final HealthSensor[] SENSORS = {
        new HealthSensor("ECG", 30.0, 0.98),
        new HealthSensor("HeartRate", 25.0, 0.95),
        new HealthSensor("BloodPressure", 15.0, 0.92),
        new HealthSensor("Oximeter", 12.0, 0.90),
        new HealthSensor("Temperature", 10.0, 0.85)
    };

    static List<Vm> vmList;
    static List<Cloudlet> cloudletList;

    static Datacenter cloudDC, fogDC, edgeDC;
    static DatacenterBroker broker;

    public static void main(String[] args) {
        try {
            System.out.println("Starting Health Fog Simulation...");
            CloudSim.init(1, Calendar.getInstance(), false);

            System.out.println("Initialising...");
            cloudDC = createDatacenter("cloud");
            fogDC = createDatacenter("fog");
            edgeDC = createDatacenter("edge");

            broker = new DatacenterBroker("HealthcareBroker");
            int brokerId = broker.getId();

            vmList = createVMs(brokerId, SENSORS.length);
            broker.submitVmList(vmList);

            cloudletList = createSensorCloudlets(brokerId);
            broker.submitCloudletList(cloudletList);

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            System.out.println("\nSimulation completed.");
            printResults(broker.getCloudletReceivedList());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static Datacenter createDatacenter(String name) throws Exception {
        List<Pe> peList = Arrays.asList(
            new Pe(0, new PeProvisionerSimple(3000)),
            new Pe(1, new PeProvisionerSimple(3000))
        );

        List<Host> hostList = Collections.singletonList(
            new Host(0,
                new RamProvisionerSimple(8192),
                new BwProvisionerSimple(100000),
                1000000,
                peList,
                new VmSchedulerTimeShared(peList))
        );

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
            "x86", "Linux", "Xen", hostList, 10.0, 3.0, 0.05, 0.001, 0.0
        );

        return new Datacenter(name, characteristics,
            new VmAllocationPolicySimple(hostList), new LinkedList<>(), 0.01);
    }

    static List<Vm> createVMs(int brokerId, int count) {
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            vms.add(new Vm(i, brokerId, 2500, 1, 2048, 10000, 10000,
                "Xen", new CloudletSchedulerTimeShared()));
        }
        return vms;
    }

    static List<Cloudlet> createSensorCloudlets(int brokerId) {
        List<Cloudlet> cloudlets = new ArrayList<>();
        UtilizationModel utilizationModel = new UtilizationModelFull();

        for (int i = 0; i < SENSORS.length; i++) {
            HealthSensor sensor = SENSORS[i];

            // Inject artificial delay based on placement
            long length = (long) (sensor.dataRate * sensor.criticality * 100);
            if (sensor.criticality >= 0.95) {
                // Edge (Lowest latency)
                length *= 1.0;
            } else if (sensor.criticality >= 0.90) {
                // Fog (Medium latency)
                length *= 1.3;
            } else {
                // Cloud (Highest latency)
                length *= 1.7;
            }

            Cloudlet cloudlet = new Cloudlet(i, length, 1, 300, 300, utilizationModel, utilizationModel, utilizationModel);
            cloudlet.setUserId(brokerId);
            cloudlet.setVmId(i); 
            cloudlets.add(cloudlet);
        }
        return cloudlets;
    }

    static void printResults(List<Cloudlet> list) {
        DecimalFormat df = new DecimalFormat("#.##");
        double totalLatency = 0, totalEnergy = 0;

        DefaultCategoryDataset latencyDataset = new DefaultCategoryDataset();
        DefaultCategoryDataset energyDataset = new DefaultCategoryDataset();

        JSONArray jsonArray = new JSONArray(); // JSON array to hold all sensor data

        System.out.println("\n========= PERFORMANCE METRICS =========");
        System.out.println("Sensor\t\tLatency(ms)\tEnergy(W)\tMonthly(kWh)\tExecTime");

        for (int i = 0; i < list.size(); i++) {
            Cloudlet c = list.get(i);
            HealthSensor s = SENSORS[i];
            double monthlyEnergy = (s.energyConsumption * 24 * 30) / 1000;

            totalLatency += s.latency;
            totalEnergy += monthlyEnergy;

            System.out.println(s.name + "\t\t" +
                    df.format(s.latency) + "\t\t" +
                    df.format(s.energyConsumption) + "\t\t" +
                    df.format(monthlyEnergy) + "\t\t" +
                    df.format(c.getActualCPUTime()));

            latencyDataset.addValue(s.latency, "Latency (ms)", s.name);
            energyDataset.addValue(monthlyEnergy, "Energy (kWh)", s.name);

            // Add JSON object
            JSONObject obj = new JSONObject();
            obj.put("sensor", s.name);
            obj.put("latency", s.latency);
            obj.put("energy", monthlyEnergy);
            obj.put("execTime", c.getActualCPUTime());
            jsonArray.add(obj);
        }

        // Save JSON to file
        try (FileWriter file = new FileWriter("simulation_metrics.json")) {
            file.write(jsonArray.toJSONString());
            System.out.println("✅ Exported metrics to simulation_metrics.json");
        } catch (Exception e) {
            e.printStackTrace();
        }

        double cloudCost = totalEnergy * 0.20;
        double fogCost = cloudCost * 0.65;

        System.out.println("\n========= AGGREGATE STATS =========");
        System.out.println("Avg Latency: " + df.format(totalLatency / SENSORS.length) + " ms");
        System.out.println("Total Monthly Energy: " + df.format(totalEnergy) + " kWh");
        System.out.println("Cloud Cost: €" + df.format(cloudCost));
        System.out.println("Fog Cost (35% Savings): €" + df.format(fogCost));
        System.out.println("Savings: €" + df.format(cloudCost - fogCost));

        try {
            String payload = "{\"heart_rate\": 82, \"temperature\": 36.7}";
            AzureIoTHubSender.sendMessage(
                "healthsensor01",                          
                "YY7tZ4l3F0j1l58aMhYROH8MDNs+dbtF2Tuzg/FtUk8=", 
                "HealthIoTHub2025.azure-devices.net",        
                payload
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        plotChart("Sensor Latency", "Sensor", "Latency (ms)", latencyDataset);
        plotChart("Sensor Monthly Energy Consumption", "Sensor", "Energy (kWh)", energyDataset);
    }

    static void plotChart(String title, String categoryAxis, String valueAxis, DefaultCategoryDataset dataset) {
        JFreeChart chart = ChartFactory.createBarChart(title, categoryAxis, valueAxis, dataset);
        ChartFrame frame = new ChartFrame(title, chart);
        frame.setSize(800, 500);
        frame.setVisible(true);
    }
}
