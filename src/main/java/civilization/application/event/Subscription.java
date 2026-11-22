package civilization.application.event;

public interface Subscription extends AutoCloseable {
    @Override void close();
}
