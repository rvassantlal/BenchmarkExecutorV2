package messages;

import worker.IProcessingResult;

/**
 * @author Robin
 */
public class ProcessingResultMessage extends Message {
    private final IProcessingResult processingResult;

    public ProcessingResultMessage(IProcessingResult processingResult) {
        super(MessageType.PROCESSING_RESULT);
        this.processingResult = processingResult;
    }

    public IProcessingResult getProcessingResult() {
        return processingResult;
    }
}
