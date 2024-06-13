package org.example;

import java.util.concurrent.Executors;

public class Bootstrap {
  public static void main(String[] args) {
    var service = new InMemoryTrendBarService();
    try (var pool = Executors.newSingleThreadExecutor()) {
      pool.submit(service::updateHistory);
    }
  }
}