package civilization.application.persistence;

public class SaveException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public SaveException(String message) { super(message); }
    public SaveException(String message, Throwable cause) { super(message, cause); }
}
