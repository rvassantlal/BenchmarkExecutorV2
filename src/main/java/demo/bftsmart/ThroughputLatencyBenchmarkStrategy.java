package demo.bftsmart;

import controller.IBenchmarkStrategy;
import controller.IWorkerStatusListener;
import controller.WorkerHandler;
import util.Storage;
import worker.IProcessingResult;
import worker.ProcessInformation;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ThroughputLatencyBenchmarkStrategy implements IBenchmarkStrategy, IWorkerStatusListener {
	private final Lock lock;
	private final Condition sleepCondition;
	private final String serverCommand;
	private final String clientCommand;
	private final Set<Integer> serverWorkersIds;
	private final Set<Integer> clientWorkersIds;
	private CountDownLatch serversReadyCounter;
	private CountDownLatch clientsReadyCounter;
	private CountDownLatch measurementDeliveredCounter;
	private int round;
	private int nRounds;
	private int dataSize;
	private boolean isWrite;
	private int[] numMaxRealClients;
	private double[] avgLatency;
	private double[] latencyDev;
	private double[] avgThroughput;
	private double[] throughputDev;
	private double[] maxLatency;
	private double[] maxThroughput;

	public ThroughputLatencyBenchmarkStrategy() {
		this.lock = new ReentrantLock(true);
		this.sleepCondition = lock.newCondition();
		String initialCommand = "java -Xmx28g -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* ";
		this.serverCommand = initialCommand + "bftsmart.benchmark.ThroughputLatencyServer ";
		this.clientCommand = initialCommand + "bftsmart.benchmark.ThroughputLatencyClient ";
		this.serverWorkersIds = new HashSet<>();
		this.clientWorkersIds = new HashSet<>();
	}
	@Override
	public void executeBenchmark(WorkerHandler[] workers, Properties benchmarkParameters) {
		int n = Integer.parseInt(benchmarkParameters.getProperty("experiment.n"));
		int f = Integer.parseInt(benchmarkParameters.getProperty("experiment.f"));
		String workingDirectory = benchmarkParameters.getProperty("experiment.working_directory");
		String[] tokens = benchmarkParameters.getProperty("experiment.clients_per_round").split(" ");
		dataSize = Integer.parseInt(benchmarkParameters.getProperty("experiment.data_size"));
		isWrite = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.is_write"));

		int nServerWorkers = n;
		int nClientWorkers = workers.length - nServerWorkers;
		int maxClientsPerProcess = 30;
		int nRequests = 10_000_000;
		int sleepBetweenRounds = 10;
		int[] clientsPerRound = new int[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			clientsPerRound[i] = Integer.parseInt(tokens[i]);
		}
		nRounds = clientsPerRound.length;
		numMaxRealClients = new int[nRounds];
		avgLatency = new double[nRounds];
		latencyDev = new double[nRounds];
		avgThroughput = new double[nRounds];
		throughputDev = new double[nRounds];
		maxLatency = new double[nRounds];
		maxThroughput = new double[nRounds];

		//Separate workers
		WorkerHandler[] serverWorkers = new WorkerHandler[nServerWorkers];
		WorkerHandler[] clientWorkers = new WorkerHandler[nClientWorkers];
		System.arraycopy(workers, 0, serverWorkers, 0, nServerWorkers);
		System.arraycopy(workers, nServerWorkers, clientWorkers, 0, nClientWorkers);
		WorkerHandler measurementServer = serverWorkers[0]; //it is the leader server
		WorkerHandler measurementClient = clientWorkers[0];
		Arrays.stream(serverWorkers).forEach(w -> serverWorkersIds.add(w.getWorkerId()));
		Arrays.stream(clientWorkers).forEach(w -> clientWorkersIds.add(w.getWorkerId()));

		//Setup workers
		String setupInformation = String.format("%d\t%d", n, f);
		Arrays.stream(workers).forEach(w -> w.setupWorker(setupInformation));

		round = 1;
		while (true) {
			try {
				lock.lock();
				System.out.printf("============ Round: %d ============\n", round);
				int nClients = clientsPerRound[round - 1];

				//Distribute clients per workers
				int[] clientsPerWorker = distributeClientsPerWorkers(nClientWorkers, nClients, maxClientsPerProcess);
				String vector = Arrays.toString(clientsPerWorker);
				int total = Arrays.stream(clientsPerWorker).sum();
				System.out.printf("Clients per worker: %s -> Total: %d\n", vector, total);

				//Start servers
				startServers(nServerWorkers, workingDirectory, serverWorkers);

				//Start clients
				startClients(nServerWorkers, workingDirectory, maxClientsPerProcess, nRequests, dataSize, isWrite,
						clientWorkers, measurementClient, clientsPerWorker);

				//Wait for system to stabilize
				System.out.println("Waiting 10s...");
				sleepSeconds(10);

				//Get measurements«
				getMeasurements(measurementServer, measurementClient);

				//Stop processes
				Arrays.stream(workers).forEach(WorkerHandler::stopWorker);

				round++;
				if (round > nRounds) {
					storeResumedMeasurements(numMaxRealClients, avgLatency, latencyDev, avgThroughput,
							throughputDev, maxLatency, maxThroughput);
					break;
				}

				//Wait between round
				System.out.printf("Waiting %ds before new round\n", sleepBetweenRounds);
				sleepSeconds(sleepBetweenRounds);
			} catch (InterruptedException e) {
				break;
			} finally {
				lock.unlock();
			}
		}
	}

	private void getMeasurements(WorkerHandler measurementServer, WorkerHandler measurementClient) throws InterruptedException {
		//Start measurements
		System.out.println("Starting measurements...");
		measurementClient.startProcessing();
		measurementServer.startProcessing();

		//Wait for measurements
		System.out.println("Measuring during 120s");
		sleepSeconds(120);

		//Stop measurements
		measurementClient.stopProcessing();
		measurementServer.stopProcessing();

		//Get measurement results
		System.out.println("Getting measurements...");
		measurementDeliveredCounter = new CountDownLatch(2);
		measurementClient.requestProcessingResult();
		measurementServer.requestProcessingResult();
		measurementDeliveredCounter.await();
	}

	private void startClients(int nServerWorkers, String workingDirectory, int maxClientsPerProcess, int nRequests, int dataSize, boolean isWrite, WorkerHandler[] clientWorkers, WorkerHandler measurementClient, int[] clientsPerWorker) throws InterruptedException {
		System.out.println("Starting clients...");
		clientsReadyCounter = new CountDownLatch(clientsPerWorker.length);
		int clientInitialId = nServerWorkers + 1000;

		for (int i = 0; i < clientsPerWorker.length && i < clientWorkers.length; i++) {
			int totalClientsPerWorker = clientsPerWorker[i];
			int nProcesses = totalClientsPerWorker / maxClientsPerProcess
					+ (totalClientsPerWorker % maxClientsPerProcess == 0 ? 0 : 1);
			ProcessInformation[] commands = new ProcessInformation[nProcesses];
			boolean isMeasurementWorker = clientWorkers[i].getWorkerId() == measurementClient.getWorkerId();

			for (int j = 0; j < nProcesses; j++) {
				int clientsPerProcess = Math.min(totalClientsPerWorker, maxClientsPerProcess);
				String command = clientCommand + clientInitialId + " " + clientsPerProcess
						+ " " + nRequests + " " + dataSize + " " + isWrite + " " + isMeasurementWorker;
				commands[j] = new ProcessInformation(command, workingDirectory);
				totalClientsPerWorker -= clientsPerProcess;
				clientInitialId += clientsPerProcess;
			}
			clientWorkers[i].startWorker(50, commands, this);
		}
		clientsReadyCounter.await();
	}

	private void startServers(int nServerWorkers, String workingDirectory, WorkerHandler[] serverWorkers) throws InterruptedException {
		System.out.println("Starting servers...");
		serversReadyCounter = new CountDownLatch(nServerWorkers);
		for (int i = 0; i < serverWorkers.length; i++) {
			String command = serverCommand + i;
			ProcessInformation[] commands = {
					new ProcessInformation(command, workingDirectory)
			};
			serverWorkers[i].startWorker(0, commands, this);
		}
		serversReadyCounter.await();
	}

	private int[] distributeClientsPerWorkers(int nClientWorkers, int nClients, int maxClientsPerProcess) {
		if (nClients == 1) {
			return new int[]{1};
		}
		if (nClientWorkers < 2) {
			return new int[]{nClients};
		}
		nClients--;//remove measurement client
		if (nClients <= maxClientsPerProcess) {
			return new int[]{1, nClients};
		}
		nClientWorkers--; //for measurement client
		int nWorkersToUse = Math.min(nClientWorkers - 1, nClients / maxClientsPerProcess);
		int[] distribution = new int[nWorkersToUse + 1];//one for measurement worker
		Arrays.fill(distribution, nClients / nWorkersToUse);
		distribution[0] = 1;
		nClients -= nWorkersToUse * (nClients / nWorkersToUse);
		int i = 1;
		while (nClients > 0) {
			nClients--;
			distribution[i]++;
			i = (i + 1) % distribution.length;
			if (i == 0) {
				i++;
			}
		}

		return distribution;
	}

	@Override
	public void onReady(int workerId) {
		if (serverWorkersIds.contains(workerId)) {
			serversReadyCounter.countDown();
		} else if (clientWorkersIds.contains(workerId)) {
			clientsReadyCounter.countDown();
		}
	}

	@Override
	public void onEnded(int workerId) {

	}

	@Override
	public void onError(int workerId, String errorMessage) {
		if (serverWorkersIds.contains(workerId)) {
			System.err.printf("Error in server worker %d\n", workerId);
		} else if (clientWorkersIds.contains(workerId)) {
			System.err.printf("Error in client worker %d\n", workerId);
		} else {
			System.out.printf("Error in unused worker %d\n", workerId);
		}
	}

	@Override
	public void onResult(int workerId, IProcessingResult processingResult) {
		Measurement measurement = (Measurement) processingResult;
		long[][] measurements = measurement.getMeasurements();
		if (measurements.length == 1) {
			System.out.println("Received client measurement results");
			processClientMeasurementResults(measurements[0]);
		} else {
			System.out.println("Received server measurement results");
			processServerMeasurementResults(measurements[0], measurements[1], measurements[2]);
		}

		measurementDeliveredCounter.countDown();
	}

	private void storeResumedMeasurements(int[] numMaxRealClients, double[] avgLatency, double[] latencyDev,
										  double[] avgThroughput, double[] throughputDev, double[] maxLatency,
										  double[] maxThroughput) {
		String fileName = "measurements-" + dataSize + "bytes-" + (isWrite ? "write" : "read") +".csv";
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName)))) {
			resultFile.write("clients(#),avgLatency(ns),latencyDev(ns),avgThroughput(ops/s),throughputDev(ops/s),maxLatency(ns),maxThroughput(ops/s)\n");
			for (int i = 0; i < nRounds; i++) {
				int clients = numMaxRealClients[i];
				double aLat = avgLatency[i];
				double dLat = latencyDev[i];
				double aThr = avgThroughput[i];
				double dThr = throughputDev[i];
				double mLat = maxLatency[i];
				double mThr = maxThroughput[i];
				resultFile.write(String.format("%d,%f,%f,%f,%f,%f,%f\n", clients, aLat, dLat, aThr, dThr, mLat, mThr));
			}
			resultFile.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void processClientMeasurementResults(long[] latencies) {
		saveClientMeasurements(round, latencies);
		Storage st = new Storage(latencies);
		System.out.printf("Client Measurement[ms] - avg:%f dev:%f max:%d\n", st.getAverage(true) / 1000000,
				st.getDP(true) / 1000000, st.getMax(true) / 1000000);
		avgLatency[round - 1] = st.getAverage(true);
		latencyDev[round - 1] = st.getDP(true);
		maxLatency[round - 1] = st.getMax(true);
	}

	private void processServerMeasurementResults(long[] clients, long[] nRequests, long[] delta) {
		saveServerMeasurements(round, clients, nRequests, delta);
		long[] th = new long[clients.length];
		long minClients = Long.MAX_VALUE;
		long maxClients = Long.MIN_VALUE;
		int size = clients.length;
		for (int i = 0; i < size; i++) {
			minClients = Long.min(minClients, clients[i]);
			maxClients = Long.max(maxClients, clients[i]);
			th[i] = (long) (nRequests[i] / (delta[i] / 1_000_000_000.0));
		}
		Storage st = new Storage(th);
		System.out.printf("Server Measurement[ops/s] - avg:%f dev:%f max:%d | minClients:%d maxClients:%d\n",
				st.getAverage(true), st.getDP(true), st.getMax(true), minClients, maxClients);
		numMaxRealClients[round - 1] = (int) maxClients;
		avgThroughput[round - 1] = st.getAverage(true);
		throughputDev[round - 1] = st.getDP(true);
		maxThroughput[round - 1] = st.getMax(true);
	}

	public void saveServerMeasurements(int round, long[] clients, long[] nRequests, long[] delta) {
		String fileName = "server_" + round + ".csv";
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName)))) {
			int size = clients.length;
			resultFile.write("clients(#),requests(#),delta(ns)\n");
			for (int i = 0; i < size; i++) {
				resultFile.write(String.format("%d,%d,%d\n", clients[i], nRequests[i], delta[i]));
			}
			resultFile.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void saveClientMeasurements(int round, long[] latencies) {
		String fileName = "client_" + round + ".csv";
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName)))) {
			resultFile.write("latency(ns)\n");
			for (long l : latencies) {
				resultFile.write(String.format("%d\n", l));
			}
			resultFile.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void sleepSeconds(long duration) throws InterruptedException {
		lock.lock();
		Executors.newSingleThreadScheduledExecutor().schedule(() -> {
			lock.lock();
			sleepCondition.signal();
			lock.unlock();
		}, duration, TimeUnit.SECONDS);
		sleepCondition.await();
		Executors.newSingleThreadExecutor().shutdown();
		lock.unlock();
	}
}
