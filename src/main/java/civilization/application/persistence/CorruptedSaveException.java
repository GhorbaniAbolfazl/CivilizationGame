package civilization.application.persistence;

public final class CorruptedSaveException extends SaveException {
    private static final long serialVersionUID = 1L;
    public CorruptedSaveException(String message) { super(message); }
    public CorruptedSaveException(String message, Throwable cause) { super(message, cause); }
}
