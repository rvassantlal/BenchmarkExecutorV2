package worker;

public interface IWorkerEventProcessor {
	void startProcessing();

	void stopProcessing();

	IProcessingResult getProcessingResult();

	void process(String line);

	boolean isReady();

	boolean ended();
}
