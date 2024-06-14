package org.example;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Executors;

import static java.lang.Thread.sleep;
import static org.example.Period.M1;

public class Bootstrap {

  public static final int MINUTES_DELAY_REPORTING = 1;

  public static void main(String[] args) {
    var service = new InMemoryTrendBarService();
    try (var pool = Executors.newFixedThreadPool(5)) {
      // 2 background jobs, which build history upon received quotes
      pool.submit(() -> {
        while (true) {
          service.updateHistory();
          sleep(100);
        }
      });
      pool.submit(() -> {
        while (true) {
          service.updateHistory();
          sleep(150);
        }
      });

      //2 jobs, which feeds the service with quotes
      pool.submit(() -> {
        while (true) {
          var r = new Random().ints(250, 800);
          service.feed(Symbol.EURJPY, r.findAny().orElse(0));
          sleep(50);
        }
      });
      pool.submit(() -> {
        while (true) {
          var r = new Random().ints(900, 999);
          service.feed(Symbol.EURJPY, r.findAny().orElse(0));
          sleep(50);
        }
      });

      //a client job, which queries history
      pool.submit(() -> {
        var now = Instant.now().getEpochSecond();
        while (true) {
          System.out.printf("(!) reports generated once per %d minute%n", MINUTES_DELAY_REPORTING);
          for (var period : Period.values()) {
            System.out.println(period);
            var history = service.history(Symbol.EURJPY, period, now);
            for (Bar bar : history) {
              System.out.print(" - ");
              System.out.println(bar);
            }
          }
          System.out.println("---");
          sleep(Duration.ofMinutes(MINUTES_DELAY_REPORTING).toMillis());
        }
      });
    }
  }
}