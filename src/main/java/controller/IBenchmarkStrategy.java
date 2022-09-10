package controller;

import java.util.Properties;

public interface IBenchmarkStrategy {
	void executeBenchmark(WorkerHandler[] workers, Properties benchmarkParameters);
}
