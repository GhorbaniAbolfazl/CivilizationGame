package civilization.application;

public final class ActionFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ActionFailure(String message) {
        super(message);
    }
}
