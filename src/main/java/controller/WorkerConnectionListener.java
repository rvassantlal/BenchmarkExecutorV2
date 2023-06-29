package controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class WorkerConnectionListener extends Thread {
	private final Logger logger = LoggerFactory.getLogger("benchmark.controller");
	private final ServerSocket serverSocket;
	private final BenchmarkController benchmarkController;

	public WorkerConnectionListener(String listeningIP, int listeningPort, BenchmarkController benchmarkController) throws IOException {
		super("Worker connection listener");
		this.benchmarkController = benchmarkController;
		this.serverSocket = new ServerSocket();
		this.serverSocket.bind(new InetSocketAddress(listeningIP, listeningPort));
		logger.info("I am listening at {}:{}", listeningIP, listeningPort);
	}

	@Override
	public void run() {
		while (true) {
			try {
				Socket worker = serverSocket.accept();
				benchmarkController.addNewWorker(worker);
			} catch (IOException e) {
				break;
			}
		}

		logger.debug("Exiting WorkerConnectionListener");
	}

	void shutdown() {
		try {
			if (!serverSocket.isClosed())
				serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
