package messages;

/**
 * @author Robin
 */
public class ErrorMessage extends Message {
    private final String errorMessage;

    public ErrorMessage(String errorMessage) {
        super(MessageType.ERROR);
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
