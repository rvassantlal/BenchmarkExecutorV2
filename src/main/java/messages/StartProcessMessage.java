package messages;

import worker.ProcessInformation;

/**
 * @author Robin
 */
public class StartProcessMessage extends Message {
    private final ProcessInformation[] processes;
    private final int maxWaitBetweenWorkers;

    public StartProcessMessage(int maxWaitBetweenWorkers, ProcessInformation... processes) {
        super(MessageType.START_WORKER);
        this.maxWaitBetweenWorkers = maxWaitBetweenWorkers;
        this.processes = processes;
    }

    public int getMaxWaitBetweenProcessInstances() {
        return maxWaitBetweenWorkers;
    }

    public ProcessInformation[] getProcesses() {
        return processes;
    }
}
