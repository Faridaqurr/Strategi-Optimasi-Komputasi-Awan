package simple;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.*;
import java.util.*;
import java.io.*;
import java.text.DecimalFormat;

public class TSA_NoAlgorithm {

    static class SimulationResult {
        int numTasks;
        double totalCpuTime;
        double totalWaitTime;
        double avgStartTime;
        double avgExecTime;
        double avgFinishTime;
        double throughput;
        double makespan;
        double imbalanceDegree;
        double resourceUtilization;
        double totalEnergyConsumption;

        @Override
        public String toString() {
            DecimalFormat df = new DecimalFormat("0.00");
            return String.format(
                    "%-15d %-18s %-18s %-18s %-20s %-20s %-15s %-15s %-20s %-22s %-25s",
                    numTasks,
                    df.format(totalCpuTime),
                    df.format(totalWaitTime),
                    df.format(avgStartTime),
                    df.format(avgExecTime),
                    df.format(avgFinishTime),
                    df.format(throughput),
                    df.format(makespan),
                    df.format(imbalanceDegree),
                    df.format(resourceUtilization),
                    df.format(totalEnergyConsumption)
            );
        }
    }

    public static void main(String[] args) {
        String datasetPath = "datasets/SDSC/SDSCDataset.txt"; 

        int size = 0;
        try {
            String filename = datasetPath.substring(datasetPath.lastIndexOf("/") + 1);
            String numberStr = filename.replaceAll("[^0-9]", "");
            if (!numberStr.isEmpty()) size = Integer.parseInt(numberStr);
        } catch (Exception ignored) {}

        System.out.println("\n=================================================");
        System.out.println("=== Menjalankan Simulasi TANPA ALGORITMA untuk Dataset: " + datasetPath + " ===");
        System.out.println("=================================================");

        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            List<Double> dataset = loadDataset(datasetPath);
            Datacenter datacenter = createDatacenter("Datacenter_NoAlgo_" + size);
            DatacenterBroker broker = new DatacenterBroker("Broker_NoAlgo_" + size);

            List<Vm> vms = createVMs(broker.getId(), 10);
            List<Cloudlet> cloudlets = createCloudlets(broker.getId(), dataset, vms);

            broker.submitVmList(vms);
            broker.submitCloudletList(cloudlets);

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            List<Cloudlet> finished = broker.getCloudletReceivedList();
            SimulationResult result = calculateMetrics(finished, vms, dataset.size());

            System.out.println("\n\n==========================================================================================================================================================================================");
            System.out.printf("%-15s %-18s %-18s %-18s %-20s %-20s %-15s %-15s %-20s %-22s %-25s%n",
                    "Jumlah Task", "Total CPU Time", "Total Wait Time", "Avg Start Time",
                    "Avg Exec Time", "Avg Finish Time", "Throughput", "Makespan",
                    "Imbalance Degree", "Resource Utilization", "Total Energy Consumption");
            System.out.println("==========================================================================================================================================================================================");
            System.out.println(result);
            System.out.println("==========================================================================================================================================================================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<Double> loadDataset(String path) throws IOException {
        List<Double> data = new ArrayList<>();
        java.io.File file = new java.io.File(path);
        if (!file.exists()) throw new FileNotFoundException("Dataset file not found: " + path);

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) data.add(Double.parseDouble(line.trim()));
            }
        }
        System.out.println("Jumlah data dimuat: " + data.size());
        return data;
    }

    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new Pe(0, new PeProvisionerSimple(1000)));
            hostList.add(new Host(
                    i, new RamProvisionerSimple(2048),
                    new BwProvisionerSimple(10000),
                    1000000, peList, new VmSchedulerTimeShared(peList)
            ));
        }
        return new Datacenter(
                name,
                new DatacenterCharacteristics("x86", "Linux", "Xen", hostList, 10.0, 3.0, 0.05, 0.1, 0.1),
                new VmAllocationPolicySimple(hostList),
                new LinkedList<>(),
                0
        );
    }

    private static List<Vm> createVMs(int brokerId, int count) {
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            vms.add(new Vm(
                    i, brokerId, 1000, 1, 512, 1000, 10000,
                    "Xen", new CloudletSchedulerTimeShared()
            ));
        }
        return vms;
    }

    private static List<Cloudlet> createCloudlets(int brokerId, List<Double> dataset, List<Vm> vms) {
        List<Cloudlet> list = new ArrayList<>();
        UtilizationModel util = new UtilizationModelFull();
        int id = 0;
        for (double val : dataset) {
            Cloudlet c = new Cloudlet(id, (long) val, 1, 300, 300, util, util, util);
            c.setUserId(brokerId);

            Vm vm = vms.get(id % vms.size());
            c.setVmId(vm.getId());

            list.add(c);
            id++;
        }
        return list;
    }

    private static SimulationResult calculateMetrics(List<Cloudlet> finished, List<Vm> vms, int numTasks) {
        SimulationResult result = new SimulationResult();
        result.numTasks = numTasks;

        double totalCpu = 0, totalWait = 0, start = 0, exec = 0, finish = 0, makespan = 0;
        for (Cloudlet c : finished) {
            totalCpu += c.getActualCPUTime();
            totalWait += c.getWaitingTime();
            start += c.getExecStartTime();
            exec += c.getActualCPUTime();
            finish += c.getFinishTime();
            makespan = Math.max(makespan, c.getFinishTime());
        }

        result.totalCpuTime = totalCpu;
        result.totalWaitTime = totalWait;
        result.avgStartTime = start / finished.size();
        result.avgExecTime = exec / finished.size();
        result.avgFinishTime = finish / finished.size();
        result.makespan = makespan;
        result.throughput = finished.size() / makespan;
        result.resourceUtilization = totalCpu / (vms.size() * makespan);
        
        Map<Integer, Double> vmLoad = new HashMap<>();
        for (Vm vm : vms) {
            vmLoad.put(vm.getId(), 0.0);
        }

        for (Cloudlet c : finished) {
            if (c.getCloudletStatus() == Cloudlet.SUCCESS) {
                vmLoad.put(c.getVmId(), vmLoad.get(c.getVmId()) + c.getActualCPUTime());
            }
        }

        double totalLoad = 0;
        for (double load : vmLoad.values()) {
            totalLoad += load;
        }
        double avgLoad = totalLoad / vms.size();

        double variance = 0;
        for (double load : vmLoad.values()) {
            variance += Math.pow(load - avgLoad, 2);
        }
        result.imbalanceDegree = Math.sqrt(variance / vms.size());


        double powerPerHost = 200;
        double idlePowerRatio = 0.6;
        int numHosts = vms.size();
        result.totalEnergyConsumption = (powerPerHost *
                (idlePowerRatio + result.resourceUtilization * (1 - idlePowerRatio))) *
                makespan * numHosts * 0.001;

        return result;
    }
}
