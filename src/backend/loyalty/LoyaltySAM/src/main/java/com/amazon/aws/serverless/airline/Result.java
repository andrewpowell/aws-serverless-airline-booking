package com.amazon.aws.serverless.airline;

import lombok.Getter;
import lombok.Setter;

public class Result {

    @Getter
    @Setter
    private int points;

    @Getter
    @Setter
    private int remainingPoints;

    @Getter
    @Setter
    private String level;

}
