package civilization.model.map;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class VisibilityState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Set<HexCoord> discovered = new HashSet<>();
    private final Set<HexCoord> currentlyVisible = new HashSet<>();
    private final Set<HexCoord> discoveredByExplorer = new HashSet<>();

    public VisibilityState() {
    }

    public VisibilityState(Set<HexCoord> discovered, Set<HexCoord> currentlyVisible) {
        this(discovered, currentlyVisible, Set.of());
    }

    public VisibilityState(
            Set<HexCoord> discovered,
            Set<HexCoord> currentlyVisible,
            Set<HexCoord> discoveredByExplorer) {
        this.discovered.addAll(Set.copyOf(Objects.requireNonNull(discovered, "discovered")));
        Set<HexCoord> explorerCopy = Set.copyOf(Objects.requireNonNull(
                discoveredByExplorer, "discoveredByExplorer"));
        this.discovered.addAll(explorerCopy);
        this.discoveredByExplorer.addAll(explorerCopy);
        replaceCurrentlyVisible(currentlyVisible);
    }

    public boolean isDiscovered(HexCoord coordinate) {
        return discovered.contains(coordinate);
    }

    public boolean isCurrentlyVisible(HexCoord coordinate) {
        return currentlyVisible.contains(coordinate);
    }

    public boolean isDiscoveredByExplorer(HexCoord coordinate) {
        return discoveredByExplorer.contains(coordinate);
    }

    public void discover(HexCoord coordinate) {
        discovered.add(Objects.requireNonNull(coordinate, "coordinate"));
    }

    public void discoverAll(Set<HexCoord> coordinates) {
        discovered.addAll(Set.copyOf(Objects.requireNonNull(coordinates, "coordinates")));
    }

    public void discoverAllByExplorer(Set<HexCoord> coordinates) {
        Set<HexCoord> copy = Set.copyOf(Objects.requireNonNull(coordinates, "coordinates"));
        discovered.addAll(copy);
        discoveredByExplorer.addAll(copy);
    }

    public void replaceCurrentlyVisible(Set<HexCoord> coordinates) {
        currentlyVisible.clear();
        Set<HexCoord> copy = Set.copyOf(Objects.requireNonNull(coordinates, "coordinates"));
        currentlyVisible.addAll(copy);
        discovered.addAll(copy);
    }

    public Set<HexCoord> discoveredHexes() {
        return Collections.unmodifiableSet(discovered);
    }

    public Set<HexCoord> currentlyVisibleHexes() {
        return Collections.unmodifiableSet(currentlyVisible);
    }

    public Set<HexCoord> explorerDiscoveredHexes() {
        return Collections.unmodifiableSet(discoveredByExplorer);
    }
}
