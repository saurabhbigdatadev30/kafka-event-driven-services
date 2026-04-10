package com.microservices.demo.twitter.to.kafka.service.runner.impl;

import twitter4j.StallWarning;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;

public class TestAnnnonymousStatusListener {
    public static void main(String[] args) {
        // Implement the StatusListener using Anonymous class
        StatusListener statusListener = new StatusListener() {
            @Override
            public void onStatus(twitter4j.Status status) {
                System.out.println("Status: " + status.getText());
            }

            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
            }

            @Override
            public void onTrackLimitationNotice(int i) {
            }

            @Override
            public void onScrubGeo(long l, long l1) {
            }

            @Override
            public void onStallWarning(StallWarning stallWarning) {
            }

            @Override
            public void onException(Exception ex) {
                ex.printStackTrace();
            }
        };
    }


}
