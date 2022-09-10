package messages;

/**
 * @author Robin
 */
public class StringMessage extends Message {
    private final String string;

    public StringMessage(MessageType messageType, String string) {
        super(messageType);
        this.string = string;
    }

    public String getString() {
        return string;
    }
}
