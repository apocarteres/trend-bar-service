package org.example;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.Executors;

import static java.lang.System.exit;
import static java.lang.Thread.sleep;
import static org.example.Period.M1;

public class Bootstrap {

  public static final int MINUTES_DELAY_REPORTING = 1;

  public static void main(String[] args) {
    var service = new InMemoryTrendBarService();
    try (var pool = Executors.newFixedThreadPool(5)) {
      // a background job, which build history upon received quotes. Currently,
      // we can use 1 job like this, in order to simplify things.
      pool.submit(() -> {
        while (true) {
          service.updateHistory();
          sleep(100);
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
        var startedAt = Instant.now();
        while (true) {
          var elapsedTime = Instant.now().minus(startedAt.getEpochSecond(), ChronoUnit.SECONDS);
          var d = Duration.ofSeconds(elapsedTime.getEpochSecond());
          System.out.printf("elapsed %s%n", format(d));
          for (var period : Period.values()) {
            var history = service.history(Symbol.EURJPY, period, startedAt.getEpochSecond());
            if (history.isEmpty()) {
              continue;
            }
            System.out.printf("%s [%d entries]%n", period, history.size());
            for (Bar bar : history) {
              System.out.print(" - ");
              System.out.printf("open %s close %s, %s%n", formatTime(bar.openAt()), formatTime(bar.closedAt()), bar);
            }
          }
          System.out.println("---");
          sleep(Duration.ofSeconds(1).toMillis());
        }
      });
    }
  }

  private static String formatTime(long unixTs) {
    return new SimpleDateFormat("HH:mm:ss").format(new Date(unixTs * 1000L));
  }

  private static String format(Duration duration) {
    return duration.toString()
      .substring(2)
      .replaceAll("(\\d[HMS])(?!$)", "$1 ")
      .toLowerCase();
  }
}