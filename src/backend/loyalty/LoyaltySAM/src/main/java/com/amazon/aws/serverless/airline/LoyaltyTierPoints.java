package com.amazon.aws.serverless.airline;

public enum LoyaltyTierPoints {

    PLATINUM(75000),
    GOLD(50000),
    SILVER(25000),
    BRONZE(10);

    private final int points;

    private LoyaltyTierPoints(int points) {
        this.points = points;
    }

    public int value() {
        return this.points;
    }
}
