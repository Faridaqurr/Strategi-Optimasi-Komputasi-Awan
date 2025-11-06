package simple;

import java.util.*;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

public class TreeSeedScheduler {

    private List<Vm> vms;
    private List<Cloudlet> cloudlets;
    private int populationSize = 10;
    private int maxIter = 20;
    private Random rand = new Random();
    private RandomForest randomForest;

    private Map<Integer, Integer> bestMapping;
    private double bestFitness = Double.MAX_VALUE;

    public TreeSeedScheduler(List<Vm> vms, List<Cloudlet> cloudlets) {
        this.vms = vms;
        this.cloudlets = cloudlets;
        this.randomForest = new RandomForest();
    }

    public void optimizeMapping() {
        System.out.println("🔄 Memulai optimasi TSA Balanced+EnergyAware...");
        long startTime = System.currentTimeMillis();

        List<Map<Integer, Integer>> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            population.add(randomMapping());
        }

        for (int iter = 0; iter < maxIter; iter++) {
            List<Map<Integer, Integer>> newPopulation = new ArrayList<>();

            for (Map<Integer, Integer> individual : population) {
                double fitness = evaluate(individual);
                if (fitness < bestFitness) {
                    bestFitness = fitness;
                    bestMapping = new HashMap<>(individual);
                }
            }

            for (int i = 0; i < populationSize; i++) {
                Map<Integer, Integer> parent = population.get(rand.nextInt(populationSize));
                Map<Integer, Integer> seed = new HashMap<>(parent);

                for (Integer cloudletId : seed.keySet()) {
                    if (rand.nextDouble() < 0.25) { 
                        int currentVmIndex = seed.get(cloudletId);

                        int bestVm = findLeastLoadedVm(seed);
                        double rfPrediction = randomForest.predict(
                                new double[]{getCloudletLength(cloudletId) / 1000.0, vms.get(currentVmIndex).getMips() / 1000.0});

                        if (rfPrediction < 0.5) {
                            seed.put(cloudletId, bestVm);
                        }
                    }
                }
                newPopulation.add(seed);
            }

            population = newPopulation;

            if ((iter + 1) % 5 == 0 || iter == maxIter - 1) {
                System.out.printf("Iterasi %d/%d → Fitness terbaik: %.4f%n", iter + 1, maxIter, bestFitness);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("✅ Optimasi selesai dalam " + (duration / 1000.0) + " detik.");
    }

    public Map<Integer, Integer> getBestMapping() {
        return bestMapping;
    }

    private Map<Integer, Integer> randomMapping() {
        Map<Integer, Integer> mapping = new HashMap<>();
        for (Cloudlet c : cloudlets) {
            mapping.put((int) c.getCloudletId(), rand.nextInt(vms.size()));
        }
        return mapping;
    }

    private double evaluate(Map<Integer, Integer> mapping) {
        Map<Integer, List<Cloudlet>> vmToCloudlets = new HashMap<>();
        for (int i = 0; i < vms.size(); i++) {
            vmToCloudlets.put(i, new ArrayList<>());
        }

        for (Cloudlet c : cloudlets) {
            int vmIndex = mapping.get((int) c.getCloudletId());
            vmToCloudlets.get(vmIndex).add(c);
        }

        double[] vmLoad = new double[vms.size()];
        for (int i = 0; i < vms.size(); i++) {
            double totalTime = 0;
            for (Cloudlet c : vmToCloudlets.get(i)) {
                totalTime += c.getCloudletLength() / vms.get(i).getMips();
            }
            vmLoad[i] = totalTime;
        }

        double makespan = Arrays.stream(vmLoad).max().orElse(0);
        double avgLoad = Arrays.stream(vmLoad).average().orElse(0);

        double imbalance = 0;
        for (double l : vmLoad) imbalance += Math.abs(l - avgLoad);
        imbalance /= vms.size();

        double totalEnergy = 0;
        for (double load : vmLoad) totalEnergy += 200 * load * 0.1;

        return makespan * 0.5 + imbalance * 0.35 + totalEnergy * 0.15;
    }

    private int findLeastLoadedVm(Map<Integer, Integer> mapping) {
        double[] vmLoad = new double[vms.size()];
        for (Cloudlet c : cloudlets) {
            int vmIndex = mapping.get((int) c.getCloudletId());
            vmLoad[vmIndex] += c.getCloudletLength() / vms.get(vmIndex).getMips();
        }
        int minIndex = 0;
        double minLoad = vmLoad[0];
        for (int i = 1; i < vmLoad.length; i++) {
            if (vmLoad[i] < minLoad) {
                minLoad = vmLoad[i];
                minIndex = i;
            }
        }
        return minIndex;
    }

    private double getCloudletLength(int id) {
        for (Cloudlet c : cloudlets) {
            if (c.getCloudletId() == id) return c.getCloudletLength();
        }
        return 0;
    }
}
