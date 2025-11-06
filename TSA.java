package simple;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.*;
import java.util.*;
import java.io.*;
import java.text.DecimalFormat;

public class TSA {

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
        System.out.println("=== Menjalankan Simulasi untuk Dataset: " + datasetPath + " ===");
        System.out.println("=================================================");

        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            List<Double> dataset = loadDataset(datasetPath);
            Datacenter datacenter = createDatacenter("Datacenter_TSA_" + size);
            DatacenterBroker broker = new DatacenterBroker("Broker_" + size);

            List<Vm> vms = createVMs(broker.getId(), 10);
            List<Cloudlet> cloudlets = createCloudlets(broker.getId(), dataset);

            broker.submitVmList(vms);

            TreeSeedScheduler scheduler = new TreeSeedScheduler(vms, cloudlets);
            scheduler.optimizeMapping();
            Map<Integer, Integer> bestMap = scheduler.getBestMapping();

            for (Cloudlet c : cloudlets) {
                int vmIndex = bestMap.get((int) c.getCloudletId());
                c.setVmId(vms.get(vmIndex).getId());
            }
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

    private static List<Cloudlet> createCloudlets(int brokerId, List<Double> dataset) {
        List<Cloudlet> list = new ArrayList<>();
        UtilizationModel util = new UtilizationModelFull();
        int id = 0;
        for (double val : dataset) {
            Cloudlet c = new Cloudlet(id++, (long) val, 1, 300, 300, util, util, util);
            c.setUserId(brokerId);
            list.add(c);
        }
        return list;
    }

    private static SimulationResult calculateMetrics(List<Cloudlet> finishedCloudlets, List<Vm> vms, int numTasks) {
        SimulationResult result = new SimulationResult();
        result.numTasks = numTasks;

        double totalCpuTime = 0;
        double totalWaitTime = 0;
        double totalStartTime = 0;
        double totalExecTime = 0;
        double totalFinishTime = 0;
        double maxFinishTime = 0;

        Map<Integer, Double> vmTotalLoad = new HashMap<>();
        for (Vm vm : vms) {
            vmTotalLoad.put(vm.getId(), 0.0);
        }

        for (Cloudlet cloudlet : finishedCloudlets) {
            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                totalCpuTime += cloudlet.getActualCPUTime();
                totalWaitTime += cloudlet.getWaitingTime();
                totalStartTime += cloudlet.getExecStartTime();
                totalExecTime += cloudlet.getActualCPUTime();
                totalFinishTime += cloudlet.getFinishTime();

                if (cloudlet.getFinishTime() > maxFinishTime) {
                    maxFinishTime = cloudlet.getFinishTime();
                }

                double vmLoad = vmTotalLoad.getOrDefault(cloudlet.getVmId(), 0.0);
                vmLoad += cloudlet.getActualCPUTime();
                vmTotalLoad.put(cloudlet.getVmId(), vmLoad);
            }
        }

        result.totalCpuTime = totalCpuTime;
        result.totalWaitTime = totalWaitTime;
        result.avgStartTime = finishedCloudlets.isEmpty() ? 0 : totalStartTime / finishedCloudlets.size();
        result.avgExecTime = finishedCloudlets.isEmpty() ? 0 : totalExecTime / finishedCloudlets.size();
        result.avgFinishTime = finishedCloudlets.isEmpty() ? 0 : totalFinishTime / finishedCloudlets.size();
        result.makespan = maxFinishTime;

        result.throughput = (maxFinishTime == 0) ? 0 : finishedCloudlets.size() / maxFinishTime;

        double sumLoad = 0;
        for (double load : vmTotalLoad.values()) sumLoad += load;
        double avgLoad = sumLoad / vmTotalLoad.size();

        double variance = 0;
        for (double load : vmTotalLoad.values()) {
            variance += Math.pow(load - avgLoad, 2);
        }
        result.imbalanceDegree = Math.sqrt(variance / vmTotalLoad.size());

        result.resourceUtilization = (vms.size() * maxFinishTime == 0) ? 0 : totalCpuTime / (vms.size() * maxFinishTime);
        double energyPerVM = 200.0; 
        result.totalEnergyConsumption = vms.size() * energyPerVM * result.resourceUtilization * 0.1;

        return result;
    }
}