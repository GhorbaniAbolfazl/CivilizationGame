package civilization.model.infrastructure;

public sealed interface Infrastructure extends java.io.Serializable permits TileInfrastructure, EdgeInfrastructure {
    InfrastructureId id();
}
