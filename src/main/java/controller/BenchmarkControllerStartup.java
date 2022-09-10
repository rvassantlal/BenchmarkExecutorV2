package controller;

import util.Configuration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Properties;

public class BenchmarkControllerStartup {

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.out.println("USAGE: ... controller.BenchmarkControllerStartup <config file>");
			System.exit(-1);
		}

		String configFile = args[0];
		Properties properties = configFileToProperties(configFile);
		startController(properties);
	}

	public static void startController(Properties properties) throws Exception {
		Configuration.setConfigurationProperties(properties);
		Configuration configuration = Configuration.getInstance();

		IBenchmarkStrategy benchmarkStrategy =
				createBenchmarkStrategy(configuration.getBenchmarkStrategyClass());
		if (benchmarkStrategy == null)
			throw new IllegalStateException("Benchmark strategy is null");

		BenchmarkController benchmarkController = new BenchmarkController(benchmarkStrategy);
		Runtime.getRuntime().addShutdownHook(new Thread(benchmarkController::shutdown));
		benchmarkController.start();
	}

	private static Properties configFileToProperties(String configFile) throws IOException {
		Properties properties = new Properties();
		try (BufferedReader in = new BufferedReader(new FileReader(configFile))) {
			String line;
			while ((line = in.readLine()) != null) {
				if (line.startsWith("#")) {
					continue;
				}
				String[] tokens = line.split("=");
				if (tokens.length != 2)
					continue;
				String propertyName = tokens[0].trim();
				String value = tokens[1].trim();
				properties.setProperty(propertyName, value);
			}
		}
		return properties;
	}

	private static IBenchmarkStrategy createBenchmarkStrategy(String className) throws Exception {
		Class<?> clazz = Class.forName(className);
		for (Constructor<?> constructor : clazz.getConstructors()) {
			Class<?>[] paramTypes = constructor.getParameterTypes();
			if (paramTypes.length == 0) {
				return (IBenchmarkStrategy) constructor.newInstance();
			}
		}
		return null;
	}
}
