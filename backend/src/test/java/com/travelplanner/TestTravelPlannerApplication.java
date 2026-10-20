package com.travelplanner;

import org.springframework.boot.SpringApplication;

public class TestTravelPlannerApplication {

    public static void main(String[] args) {
        SpringApplication.from(TravelPlannerApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("dev")
                .run(args);
    }
}
