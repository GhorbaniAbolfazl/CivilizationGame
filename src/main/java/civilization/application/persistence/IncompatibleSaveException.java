package civilization.application.persistence;

public final class IncompatibleSaveException extends SaveException {
    private static final long serialVersionUID = 1L;
    public IncompatibleSaveException(String message) { super(message); }
}
