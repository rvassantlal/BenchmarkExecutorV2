package util;

import java.util.Map;
import java.util.Properties;

public final class Configuration {
    private final String listeningIP;
    private final int listeningPort;
    private final int numWorkers;
    private final String benchmarkStrategyClass;
    private final String workerSetupClass;
    private final String workerEventProcessor;
    private final Properties benchmarkParameters;
    private static Configuration INSTANT;

    public static void setConfigurationProperties(Properties properties) {
        if (INSTANT == null) {
            INSTANT = new Configuration(properties);
        }
    }

    public static Configuration getInstance() {
        return INSTANT;
    }

    private Configuration(Properties configurationProperties) {
        listeningIP = configurationProperties.getProperty("controller.listening.ip");
        listeningPort = Integer.parseInt(configurationProperties.getProperty("controller.listening.port"));
        numWorkers = Integer.parseInt(configurationProperties.getProperty("global.worker.machines"));
        benchmarkStrategyClass = configurationProperties.getProperty("controller.benchmark.strategy");
        workerSetupClass = configurationProperties.getProperty("controller.worker.setup");
        workerEventProcessor = configurationProperties.getProperty("controller.worker.processor");
        benchmarkParameters = new Properties();

        for (Map.Entry<Object, Object> entry : configurationProperties.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            switch (key) {
                case "controller.listening.ip":
                case "controller.listening.port":
                case "global.worker.machines":
                case "controller.benchmark.strategy":
                case "controller.worker.setup":
                case "controller.worker.processor":
                    continue;
            }
            benchmarkParameters.put(key, value);
        }
    }

    public Properties getBenchmarkParameters() {
        return benchmarkParameters;
    }

    public String getBenchmarkStrategyClass() {
        return benchmarkStrategyClass;
    }

    public String getListeningIP() {
        return listeningIP;
    }

    public int getListeningPort() {
        return listeningPort;
    }

    public int getNumWorkers() {
        return numWorkers;
    }

    public String getWorkerSetupClass() {
        return workerSetupClass;
    }

    public String getWorkerEventProcessor() {
        return workerEventProcessor;
    }
}
