package civilization.application.event;

public record TurnResolutionStartedEvent(int turnNumber) implements GameEvent { }
