package controller;

import messages.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import worker.ProcessInformation;

import java.io.*;
import java.net.Socket;

public class WorkerHandler extends Thread {
	private final Logger logger = LoggerFactory.getLogger("benchmark.controller");
	private final int workerId;
	private final Socket connection;
	private final ObjectOutput out;
	private IWorkerStatusListener workerStatusListener;

	public WorkerHandler(int workerId, Socket connection) throws IOException {
		this.workerId = workerId;
		this.connection = connection;
		this.out = new ObjectOutputStream(connection.getOutputStream());
	}

	public int getWorkerId() {
		return workerId;
	}

	@Override
	public void run() {
		try (ObjectInput in = new ObjectInputStream(connection.getInputStream())) {
			boolean isWork = true;
			while (isWork) {
				Message msg = (Message) in.readObject();
				switch (msg.getType()) {
					case WORKER_READY:
						workerStatusListener.onReady(workerId);
						break;
					case WORKER_ENDED:
						workerStatusListener.onEnded(workerId);
						break;
					case PROCESSING_RESULT:
						workerStatusListener.onResult(workerId, ((ProcessingResultMessage)msg).getProcessingResult());
						break;
					case ERROR:
						workerStatusListener.onError(workerId, ((ErrorMessage)msg).getErrorMessage());
						break;
					default:
						logger.error("Unexpected value: " + msg.getType());
						isWork = false;
				}
			}
		} catch (IOException | ClassNotFoundException ignored) {}
	}

	public void setSetupClass(String className) {
		StringMessage msg = new StringMessage(MessageType.SET_SETUP_CLASS, className);
		send(msg);
	}

	public void setWorkerEventProcessorClass(String className) {
		StringMessage msg = new StringMessage(MessageType.SET_WORKER_EVENT_PROCESSOR_CLASS, className);
		send(msg);
	}

	public void setupWorker(String setupInformation) {
		StringMessage msg = new StringMessage(MessageType.SETUP_WORKER, setupInformation);
		send(msg);
	}
	public void startWorker(int maxWaitBetweenWorkers, ProcessInformation[] commands, IWorkerStatusListener workerStatusListener) {
		this.workerStatusListener = workerStatusListener;
		StartProcessMessage msg = new StartProcessMessage(maxWaitBetweenWorkers, commands);
		send(msg);
	}

	public void stopWorker() {
		StandardMessage msg = new StandardMessage(MessageType.STOP_WORKER);
		send(msg);
	}

	public void startProcessing() {
		StandardMessage msg = new StandardMessage(MessageType.START_PROCESSING);
		send(msg);
	}

	public void stopProcessing() {
		StandardMessage msg = new StandardMessage(MessageType.STOP_PROCESSING);
		send(msg);
	}

	public void requestProcessingResult() {
		StandardMessage msg = new StandardMessage(MessageType.GET_PROCESSING_RESULT);
		send(msg);
	}
	public void shutdown() {
		StandardMessage msg = new StandardMessage(MessageType.TERMINATE_WORKER);
		send(msg);
		try {
			if (connection.isConnected()) {
				connection.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void send(Message message) {
		try {
			out.writeObject(message);
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
