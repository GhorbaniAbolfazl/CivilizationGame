package civilization.model.random;

import java.util.Objects;

/**
 * Small deterministic generator with a single portable 64-bit state.
 * The algorithm name and version are part of every persistence snapshot.
 */
public final class SplitMix64GameplayRandom implements GameplayRandom {
    private static final long serialVersionUID = 1L;
    public static final String ALGORITHM = "splitmix64";
    public static final int VERSION = 1;
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    public SplitMix64GameplayRandom(long seed) {
        this.state = seed;
    }

    public SplitMix64GameplayRandom(GameplayRandomSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!ALGORITHM.equals(snapshot.algorithm()) || snapshot.version() != VERSION) {
            throw new IllegalArgumentException("Unsupported gameplay random state: " + snapshot);
        }
        this.state = snapshot.state();
    }

    @Override
    public long nextLong() {
        state += GOLDEN_GAMMA;
        long value = state;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Random bound must be positive");
        }
        long mask = bound - 1L;
        long candidate = nextLong() >>> 1;
        if ((bound & mask) == 0L) {
            return (int) (candidate & mask);
        }
        long result = candidate % bound;
        while (candidate + mask - result < 0L) {
            candidate = nextLong() >>> 1;
            result = candidate % bound;
        }
        return (int) result;
    }

    @Override
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    @Override
    public GameplayRandomSnapshot snapshot() {
        return new GameplayRandomSnapshot(ALGORITHM, VERSION, state);
    }
}
