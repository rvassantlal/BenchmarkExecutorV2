package worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessInstance extends Thread {
	private final Logger logger = LoggerFactory.getLogger("benchmark.worker");
	private final Logger processLogger = LoggerFactory.getLogger("benchmark.worker.process");
	private final EventTrigger eventTrigger;
	private final IWorkerEventProcessor eventProcessor;
	private final Process process;
	private boolean isReady;

	ProcessInstance(String workingDir, String command, EventTrigger eventTrigger,
					IWorkerEventProcessor eventProcessor) throws IOException {
		super("Worker Thread");
		this.eventTrigger = eventTrigger;
		this.eventProcessor = eventProcessor;
		logger.info("Executing '{}' in '{}'", command, workingDir);
		this.process = Runtime.getRuntime().exec(command, null, new File(workingDir));
	}

	@Override
	public void run() {
		ErrorMonitor errorMonitor = new ErrorMonitor(process.getErrorStream(), eventTrigger);
		errorMonitor.start();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = in.readLine()) != null) {
				processLogger.debug("{}", line);
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
		logger.debug("Exiting ProcessInstance");
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
