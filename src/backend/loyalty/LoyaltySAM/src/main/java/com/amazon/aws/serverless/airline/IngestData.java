package com.amazon.aws.serverless.airline;

import lombok.Getter;
import lombok.Setter;

public class IngestData {

    @Getter
    @Setter
    private String customerId;

    @Getter
    @Setter
    private int price;

}
