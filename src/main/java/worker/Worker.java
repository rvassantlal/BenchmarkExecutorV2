package worker;

import messages.*;

import javax.net.SocketFactory;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Worker extends Thread {
	private final Socket socket;
	private final LinkedList<ProcessInstance> processInstances;
	private int totalProcessInstances;
	private final AtomicInteger numReadProcessInstances;
	private final Random rndGenerator;

	public Worker(String controllerIP, int controllerPort) throws IOException {
		super("Worker thread");
		this.socket = SocketFactory.getDefault().createSocket(controllerIP, controllerPort);
		String localBoundedIp = socket.getLocalAddress().getHostAddress();
		int localBoundedPort = socket.getLocalPort();
		System.out.printf("Connected from %s:%d to %s:%d%n", localBoundedIp, localBoundedPort,
				controllerIP, controllerPort);
		this.processInstances = new LinkedList<>();
		this.rndGenerator = new Random();
		this.numReadProcessInstances = new AtomicInteger(0);
	}

	@Override
	public void run() {
		try (ObjectInput in = new ObjectInputStream(socket.getInputStream());
			 ObjectOutput out = new ObjectOutputStream(socket.getOutputStream())) {
			SynchronizedSender sender = new SynchronizedSender(out);
			ISetupWorker setupWorker = null;
			Class<?> workerEventProcessorClass = null;
			boolean doWork = true;
			while (doWork) {
				Message message = (Message) in.readObject();
				//System.out.printf("Received message: %s\n", message.getType());
				switch (message.getType()) {
					case TERMINATE_WORKER:
						doWork = false;
						break;
					case SET_SETUP_CLASS:
						StringMessage setupClassMessage = (StringMessage) message;
						setupWorker = createSetupWorkerInstance(Class.forName(setupClassMessage.getString()));
						break;
					case SET_WORKER_EVENT_PROCESSOR_CLASS:
						StringMessage setupWorkerEventProcessClassMessage = (StringMessage) message;
						workerEventProcessorClass = Class.forName(setupWorkerEventProcessClassMessage.getString());
						break;
					case SETUP_WORKER:
						StringMessage setupMessage = (StringMessage) message;
						if (setupWorker != null)
							setupWorker.setup(setupMessage.getString());
						break;
					case START_WORKER:
						StartProcessMessage startProcessMessage = (StartProcessMessage) message;
						totalProcessInstances = startProcessMessage.getProcesses().length;
						numReadProcessInstances.set(0);
						int maxWaitBetweenProcesses = Math.max(1, startProcessMessage.getMaxWaitBetweenProcessInstances());
						for (ProcessInformation process : startProcessMessage.getProcesses()) {
							ProcessInstance processInstance = createProcessInstance(sender, process, workerEventProcessorClass);
							processInstances.add(processInstance);
							processInstance.start();
							int sleepTime = rndGenerator.nextInt(maxWaitBetweenProcesses);
							Thread.sleep(sleepTime);
						}
						break;
					case STOP_WORKER:
						processInstances.forEach(ProcessInstance::shutdown);
						processInstances.clear();
						break;
					case START_PROCESSING:
						processInstances.forEach(p -> p.changeProcessingStateTo(true));
						break;
					case STOP_PROCESSING:
						processInstances.forEach(p -> p.changeProcessingStateTo(false));
						break;
					case GET_PROCESSING_RESULT:
						//Collects only first process instance's processing result.
						//Maybe in future ask the user to implement an aggregation function that given a set
						//	processing results, it a returns a final result.
						for (ProcessInstance processInstance : processInstances) {
							IProcessingResult processingResult = processInstance.getProcessingResult();
							if (processingResult == null)
								continue;
							sender.send(new ProcessingResultMessage(processingResult));
						}
						break;
				}
			}
		} catch (IOException | ClassNotFoundException | InvocationTargetException | InstantiationException |
				 IllegalAccessException | InterruptedException e) {
			e.printStackTrace();
		} finally {
			try {
				socket.close();
				processInstances.forEach(ProcessInstance::shutdown);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private ProcessInstance createProcessInstance(SynchronizedSender sender, ProcessInformation processInfo,
												  Class<?> workerEventProcessorClass) throws InvocationTargetException, InstantiationException, IllegalAccessException, IOException {
		String command = processInfo.getCommand();
		String workingDir = processInfo.getWorkingDirectory();

		IWorkerEventProcessor eventProcessor = createEventProcessorInstance(workerEventProcessorClass);

		EventTrigger eventTrigger = new EventTrigger() {
			@Override
			void readyEvent() {
				int v = numReadProcessInstances.incrementAndGet();
				if (v == totalProcessInstances)
					sender.send(new StandardMessage(MessageType.WORKER_READY));
			}

			@Override
			void processEnded() {
				sender.send(new StandardMessage(MessageType.WORKER_ENDED));
			}

			@Override
			void error(String errorMessage) {
				sender.send(new ErrorMessage(errorMessage));
			}
		};

		return new ProcessInstance(workingDir, command, eventTrigger, eventProcessor);
	}

	private static IWorkerEventProcessor createEventProcessorInstance(Class<?> workerEventProcessorClass) throws InvocationTargetException, InstantiationException, IllegalAccessException {
		for (Constructor<?> constructor : workerEventProcessorClass.getConstructors()) {
			if (constructor.getParameterCount() == 0)
				return (IWorkerEventProcessor) constructor.newInstance();
		}
		return null;
	}

	@Override
	public void interrupt() {
		try {
			socket.close();
			processInstances.forEach(ProcessInstance::shutdown);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		super.interrupt();
	}

	private static ISetupWorker createSetupWorkerInstance(Class<?> setupWorkerClass) throws InvocationTargetException, InstantiationException, IllegalAccessException {
		for (Constructor<?> constructor : setupWorkerClass.getConstructors()) {
			if (constructor.getParameterCount() == 0)
				return (ISetupWorker) constructor.newInstance();
		}
		return null;
	}
}
