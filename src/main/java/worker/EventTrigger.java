package worker;

/**
 * @author Robin
 */
abstract class EventTrigger {

    abstract void readyEvent();

    abstract void processEnded();

    abstract void error(String errorMessage);
}
