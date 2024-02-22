package generic;

import worker.IProcessingResult;

public interface IMeasurementEventProcessor {
	void process(String line);

	void reset();

	IProcessingResult getResult();
}
