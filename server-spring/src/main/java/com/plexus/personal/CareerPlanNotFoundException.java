package com.plexus.personal;

public class CareerPlanNotFoundException extends RuntimeException {
    public CareerPlanNotFoundException() {
        super("职业规划不存在");
    }
}
