package civilization.model.random;

/** Outcome-affecting deterministic randomness with an explicit restorable state. */
public interface GameplayRandom extends java.io.Serializable {
    long nextLong();

    int nextInt(int bound);

    double nextDouble();

    GameplayRandomSnapshot snapshot();
}
