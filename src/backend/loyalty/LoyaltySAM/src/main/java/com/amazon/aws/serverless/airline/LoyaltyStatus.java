package com.amazon.aws.serverless.airline;

public enum LoyaltyStatus {

    ACTIVE("active"),
    REVOKED("revoked"),
    EXPIRED("expired");

    private final String key;

    private LoyaltyStatus(final String key) {
        this.key = key;
    }

    @Override
    public String toString() {
        return this.key;
    }
}
