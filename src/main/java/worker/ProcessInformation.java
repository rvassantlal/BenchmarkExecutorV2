package worker;

import java.io.Serializable;

/**
 * @author Robin
 */
public class ProcessInformation implements Serializable {
    private final String command;
    private final String workingDirectory;

    public ProcessInformation(String command, String workingDirectory) {
        this.command = command;
        this.workingDirectory = workingDirectory;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public String getCommand() {
        return command;
    }
}
