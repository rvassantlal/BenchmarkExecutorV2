package controller;

import util.Configuration;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class BenchmarkController extends Thread {
	private final IBenchmarkStrategy benchmarkStrategy;
	private final WorkerConnectionListener workerConnectionListener;
	private final WorkerHandler[] workerHandlers;
	private int workerHandlerIndex;
	private final CountDownLatch areWorkersConnected;

	public BenchmarkController(IBenchmarkStrategy benchmarkStrategy) throws IOException {
		super("Benchmark Controller Thread");
		this.benchmarkStrategy = benchmarkStrategy;
		Configuration configuration = Configuration.getInstance();
		String listeningIP = configuration.getListeningIP();
		int listeningPort = configuration.getListeningPort();
		int numWorkers = configuration.getNumWorkers();
		this.workerHandlers = new WorkerHandler[numWorkers];
		this.areWorkersConnected = new CountDownLatch(numWorkers);

		this.workerConnectionListener = new WorkerConnectionListener(listeningIP, listeningPort, this);
		this.workerConnectionListener.start();
	}

	@Override
	public void run() {
		try {
			areWorkersConnected.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		Configuration configuration = Configuration.getInstance();
		for (WorkerHandler workerHandler : workerHandlers) {
			workerHandler.setSetupClass(configuration.getWorkerSetupClass());
			workerHandler.setWorkerEventProcessorClass(configuration.getWorkerEventProcessor());
		}

		benchmarkStrategy.executeBenchmark(workerHandlers, configuration.getBenchmarkParameters());
		shutdown();
		//System.out.println("Exiting BenchmarkController");
	}

	void addNewWorker(Socket connection) throws IOException {
		System.out.printf("Received connection from %s:%d\n",
				connection.getInetAddress().getHostAddress(),
				connection.getPort());
		WorkerHandler workerHandler = new WorkerHandler(workerHandlerIndex, connection);
		workerHandlers[workerHandlerIndex++] = workerHandler;
		workerHandler.start();
		if (workerHandlers.length == workerHandlerIndex)
			workerConnectionListener.shutdown();
		areWorkersConnected.countDown();
	}

	public void shutdown() {
		workerConnectionListener.shutdown();
		for (WorkerHandler workerHandler : workerHandlers) {
			if (workerHandler == null)
				continue;
			workerHandler.shutdown();
		}
		Arrays.fill(workerHandlers, null);
	}
}
