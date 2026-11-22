package civilization.model.diplomacy;

public enum RelationBand {
    ENEMY, DISPLEASED, NEUTRAL, FRIENDLY, ALLIED;

    public static RelationBand fromScore(int score) {
        if (score < -100 || score > 100) throw new IllegalArgumentException("Relation score out of range");
        if (score <= -50) return ENEMY;
        if (score <= -20) return DISPLEASED;
        if (score <= 19) return NEUTRAL;
        if (score <= 69) return FRIENDLY;
        return ALLIED;
    }
}
