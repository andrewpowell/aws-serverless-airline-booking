package com.amazon.aws.serverless.airline;

public enum LoyaltyTier {
    PLATINUM("platinum"),
    GOLD("gold"),
    SILVER("silver"),
    BRONZE("bronze");

    private final String tier;

    private LoyaltyTier(String tier) {
        this.tier = tier;
    }

    public String toString() {
        return this.tier;
    }

}
