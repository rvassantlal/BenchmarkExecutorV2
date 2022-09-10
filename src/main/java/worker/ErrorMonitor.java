package worker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author Robin
 */
public class ErrorMonitor extends Thread {
    private final InputStream errorStream;
    private final EventTrigger eventTrigger;

    ErrorMonitor(InputStream errorStream, EventTrigger eventTrigger) {
        super("Error Monitor Thread");
        this.errorStream = errorStream;
        this.eventTrigger = eventTrigger;
    }

    @Override
    public void run() {
        try (BufferedReader in =
                     new BufferedReader(new InputStreamReader(errorStream))) {
            String line;
            while ((line = in.readLine()) != null) {
                System.err.println(line);
                eventTrigger.error(line);
            }
        } catch (IOException ignored) {}
        //System.out.println("Exiting ErrorPrinter");
    }
}
