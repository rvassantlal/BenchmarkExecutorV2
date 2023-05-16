package demo.bftsmart;

import worker.ISetupWorker;

public class BFTSMaRtSetup implements ISetupWorker {
	@Override
	public void setup(String setupInformation) {
		System.out.println("Setting up...");
	}
}
