package org.example;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Executors;

import static java.lang.Thread.sleep;
import static org.example.Period.M1;

public class Bootstrap {
  public static void main(String[] args) {
    var service = new InMemoryTrendBarService();
    try (var pool = Executors.newFixedThreadPool(4)) {
      // the background job which build history upon received quotes
      pool.submit(() -> {
        while (true) {
          service.updateHistory();
          sleep(100);
        }
      });

      //some job, which feeds the service with quotes
      pool.submit(() -> {
        while (true) {
          var r = new Random().ints(250, 800);
          service.feed(Symbol.EURJPY, r.findAny().orElse(0));
          sleep(10);
        }
      });

      //a client job, which queries history
      pool.submit(() -> {
        var now = Instant.now().getEpochSecond();
        while (true) {
          var history = service.history(Symbol.EURJPY, M1, now);
          for (Bar bar : history) {
            System.out.println(bar);
          }
          System.out.println("---");
          sleep(1000);
        }
      });
    }
  }
}