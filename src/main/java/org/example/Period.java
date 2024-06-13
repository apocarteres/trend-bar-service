package org.example;

import java.time.Duration;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;

public enum Period {
  M1(ofMinutes(1)), H1(ofHours(1)), D1(ofDays(1));

  private final Duration duration;

  Period(Duration duration) {
    this.duration = duration;
  }

  public Duration getDuration() {
    return duration;
  }
}