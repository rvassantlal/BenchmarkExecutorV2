package worker;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessInstance extends Thread {
	private final EventTrigger eventTrigger;
	private final IWorkerEventProcessor eventProcessor;
	private final Process process;
	private boolean isReady;

	ProcessInstance(String workingDir, String command, EventTrigger eventTrigger,
					IWorkerEventProcessor eventProcessor) throws IOException {
		super("Worker Thread");
		this.eventTrigger = eventTrigger;
		this.eventProcessor = eventProcessor;
		System.out.printf("Executing '%s'\n", command);
		this.process = Runtime.getRuntime().exec(command, null, new File(workingDir));
	}

	@Override
	public void run() {
		ErrorMonitor errorMonitor = new ErrorMonitor(process.getErrorStream(), eventTrigger);
		errorMonitor.start();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = in.readLine()) != null) {
				//System.out.println(line);
				eventProcessor.process(line);
				if (!isReady && eventProcessor.isReady()) {
					isReady = true;
					eventTrigger.readyEvent();
				}
				if (eventProcessor.ended()) {
					eventTrigger.processEnded();
				}
			}
		} catch (IOException ignored) {
		} finally {
			errorMonitor.interrupt();
		}
	}

	void changeProcessingStateTo(boolean value) {
		if (value)
			eventProcessor.startProcessing();
		else
			eventProcessor.stopProcessing();
	}

	void shutdown() {
		process.destroyForcibly();
	}

	IProcessingResult getProcessingResult() {
		return eventProcessor.getProcessingResult();
	}
}
