package com.amazon.aws.serverless.airline;

import lombok.Getter;
import lombok.Setter;

public class LoyaltyRecord {

    @Getter
    @Setter
    private String id;

    @Getter
    @Setter
    private String customerId;

    @Getter
    @Setter
    private Integer points;

    @Getter
    @Setter
    private String date;

    @Getter
    @Setter
    private String flag;

    public Result toResult() {
        Result result = new Result();
        result.setLevel(Get.level(this.points).toString());
        result.setPoints(this.points);
        result.setRemainingPoints(Get.nextTier(this.points, Get.level(this.points)));
        return result;
    }

}
