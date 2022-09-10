package messages;

import java.io.Serializable;

/**
 * @author Robin
 */
public abstract class Message implements Serializable {
    private final MessageType type;

    Message(MessageType type) {
        this.type = type;
    }

    public MessageType getType() {
        return type;
    }

}
