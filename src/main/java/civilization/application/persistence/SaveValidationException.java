package civilization.application.persistence;

public final class SaveValidationException extends SaveException {
    private static final long serialVersionUID = 1L;
    public SaveValidationException(String message) { super(message); }
    public SaveValidationException(String message, Throwable cause) { super(message, cause); }
}
