package civilization.model.technology;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable prerequisite graph; costs and effects belong to later feature definitions. */
public final class TechnologyCatalog implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<TechnologyId, TechnologyDefinition> definitions;

    public TechnologyCatalog(Collection<TechnologyDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        Map<TechnologyId, TechnologyDefinition> indexed = new HashMap<>();
        for (TechnologyDefinition definition : definitions) {
            Objects.requireNonNull(definition, "definition");
            if (indexed.put(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate technology: " + definition.id());
            }
        }
        for (TechnologyDefinition definition : indexed.values()) {
            if (!indexed.keySet().containsAll(definition.prerequisites())) {
                throw new IllegalArgumentException("Unknown prerequisite for " + definition.id());
            }
        }
        this.definitions = Map.copyOf(indexed);
        validateAcyclic();
    }

    public TechnologyDefinition require(TechnologyId id) {
        TechnologyDefinition result = definitions.get(Objects.requireNonNull(id, "id"));
        if (result == null) throw new IllegalArgumentException("Unknown technology: " + id);
        return result;
    }

    public boolean canResearch(TechnologyId id, Set<TechnologyId> researched) {
        Objects.requireNonNull(researched, "researched");
        return !researched.contains(id) && researched.containsAll(require(id).prerequisites());
    }

    public Collection<TechnologyDefinition> definitions() { return definitions.values(); }

    private void validateAcyclic() {
        Set<TechnologyId> complete = new HashSet<>();
        Set<TechnologyId> visiting = new HashSet<>();
        for (TechnologyId id : definitions.keySet()) visit(id, visiting, complete);
    }

    private void visit(TechnologyId id, Set<TechnologyId> visiting, Set<TechnologyId> complete) {
        if (complete.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("Technology cycle at " + id);
        for (TechnologyId prerequisite : require(id).prerequisites()) {
            visit(prerequisite, visiting, complete);
        }
        visiting.remove(id);
        complete.add(id);
    }
}
