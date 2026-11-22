package civilization.model.random;

/** Injectable source of valid d6 values used by deterministic combat tests. */
@FunctionalInterface
public interface DiceSource {
    int rollD6();
}
