package civilization.application.tribe;

import civilization.model.economy.ResourceType;
import civilization.model.identity.TribeId;

public record GiftResult(TribeId tribeId, ResourceType resource, int amount,
        int relationshipIncrease, int resultingRelationship) { }
