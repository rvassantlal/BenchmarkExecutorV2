package worker;

import java.io.IOException;

public class WorkerStartup {
	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.out.println("USAGE: ... worker.WorkerStartup <controller's ip> <controller's port>");
			System.exit(-1);
		}
		String controllerIP = args[0];
		int controllerPort = Integer.parseInt(args[1]);

		Worker worker = new Worker(controllerIP, controllerPort);
		worker.start();

		Runtime.getRuntime().addShutdownHook(new Thread(worker::interrupt));
	}
}
