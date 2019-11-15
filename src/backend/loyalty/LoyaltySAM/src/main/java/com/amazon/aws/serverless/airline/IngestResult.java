package com.amazon.aws.serverless.airline;

import lombok.Getter;

public class IngestResult {

    @Getter
    private final String message;

    public IngestResult(final String message) {
        this.message = message;
    }
}
