package controller;

import worker.IProcessingResult;

public interface IWorkerStatusListener {
	void onReady(int workerId);
	void onEnded(int workerId);
	void onError(int workerId, String errorMessage);
	void onResult(int workerId, IProcessingResult processingResult);
}
