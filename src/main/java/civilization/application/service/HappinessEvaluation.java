package civilization.application.service;

import civilization.model.happiness.HappinessTier;

public record HappinessEvaluation(
        int value,
        HappinessTier tier,
        int accumulatedEvents,
        int monumentContribution,
        int hallGarrisonContribution) { }
