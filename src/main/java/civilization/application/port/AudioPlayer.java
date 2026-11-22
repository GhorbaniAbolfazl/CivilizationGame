package civilization.application.port;

public interface AudioPlayer extends AutoCloseable {
    void playLoop();

    void setVolume(double normalizedVolume);

    void stop();

    @Override
    void close();
}
