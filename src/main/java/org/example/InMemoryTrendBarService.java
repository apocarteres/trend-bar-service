package org.example;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Collections.emptyList;

public final class InMemoryTrendBarService implements TrendBarService {
  private final Supplier<Long> timeSupplier;
  private final BlockingQueue<Quote> quotes;
  private final Map<Symbol, Map<Period, List<Bar>>> historyAccumulator;
  private final AtomicReference<Map<Symbol, Map<Period, List<Bar>>>> readOnlyHistory;
  private final Map<Symbol, Map<Period, BarBuilder>> barBuilders;
  private final Object historyMutex;
  private final Object buildersMutex;

  public InMemoryTrendBarService() {
    this(() -> Instant.now().getEpochSecond());
  }

  /**
   * @param timeSupplier provides time in Unix timestamp format (in seconds)
   */
  public InMemoryTrendBarService(Supplier<Long> timeSupplier) {
    this.timeSupplier = timeSupplier;
    this.quotes = new LinkedBlockingQueue<>();
    this.historyAccumulator = new HashMap<>();
    this.readOnlyHistory = new AtomicReference<>(new HashMap<>());
    this.barBuilders = new HashMap<>();
    this.historyMutex = new Object();
    this.buildersMutex = new Object();
  }

  // in order to simplify the solution, we expect a class user has to be
  // responsible on executing this method whenever required. It helps to keep
  // this class clean and move concurrency related logic outside, however on the same time,
  // this class stays thread-safe and maintain it's state properly.
  public void updateHistory() {
    //this map is to collect ready-to-go bars with no blocking global history map
    var historyCandidates = new HashMap<Symbol, Map<Period, List<Bar>>>();
    Quote quote;
    while ((quote = quotes.poll()) != null) {
      var symbol = quote.symbol();
      var ts = quote.timestamp();
      //block builders and assembly bars, move read-to-go bars into history candidates
      synchronized (buildersMutex) {
        var periods = barBuilders.computeIfAbsent(symbol, s -> new HashMap<>());
        for (var period : Period.values()) {
          var builder = periods.computeIfAbsent(period, makeBuilderFor(quote));
          if (builder.isOpen(ts)) {
            builder.update(quote.price(), ts);
          } else {
            pushCandidate(historyCandidates, symbol, period, builder.build());
            periods.remove(period);
          }
        }
      }
    }
    // if we have non-closed bar and time is out, we should close it and move to history as well.
    var now = timeSupplier.get();
    synchronized (buildersMutex) {
      for (var entry : barBuilders.entrySet()) {
        var it = entry.getValue().entrySet().iterator();
        while (it.hasNext()) {
          var pair = it.next();
          var builder = pair.getValue();
          if (!builder.isOpen(now)) {
            pushCandidate(historyCandidates, entry.getKey(), pair.getKey(), builder.build());
            it.remove();
          }
        }
      }
    }
    //finally we are ready acquiring a lock on history and update it with all candidates we
    //have collected so far.
    synchronized (historyMutex) {
      for (var entry : historyCandidates.entrySet()) {
        var symbol = entry.getKey();
        for (var builders : entry.getValue().entrySet()) {
          for (var bar : builders.getValue()) {
            pushCandidate(historyAccumulator, symbol, builders.getKey(), bar);
          }
        }
      }
      readOnlyHistory.set(new HashMap<>(historyAccumulator));
    }
  }


  @Override
  public void feed(Symbol symbol, int price, long timestamp) {
    // some thoughts:
    // - perhaps we need to handle the back-pressure case here?
    // - probably we need to fire some events for outside components
    //   that quotes updated.
    quotes.add(new Quote(symbol, price, timestamp));
  }

  @Override
  public List<Bar> history(Symbol symbol, Period period, long from) {
    return history(symbol, period, from, timeSupplier.get());
  }

  @Override
  public List<Bar> history(Symbol symbol, Period period, long from, long to) {
    return readOnlyHistory.get().getOrDefault(symbol, new HashMap<>())
      .getOrDefault(period, emptyList()).stream()
      .filter(b -> isBarEligible(from, to, b))
      .toList();
  }

  @Override
  public void feed(Symbol symbol, int price) {
    feed(symbol, price, timeSupplier.get());
  }

  private void pushCandidate(Map<Symbol, Map<Period, List<Bar>>> map, Symbol symbol, Period period, Bar bar) {
    map.computeIfAbsent(symbol, s -> new HashMap<>())
            .computeIfAbsent(period, p -> new ArrayList<>()).add(bar);
  }

  private static boolean isBarEligible(long from, long to, Bar b) {
    return (b.openAt() >= from && b.closedAt() <= to);
  }

  private static Function<Period, BarBuilder> makeBuilderFor(Quote quote) {
    return p -> new BarBuilder(quote.price(), quote.timestamp(),
            quote.timestamp() + p.getDuration().getSeconds());
  }

  private static final class BarBuilder {
    private final long openAt;
    private final long validTill;
    private final int openPrice;
    //here is volatile fields. The reason is that, during updates processing
    //we are not sure which tread will be making history. That is, those fields
    //might be updated from within different threads.
    private volatile int closePrice;
    private volatile int highPrice;
    private volatile int lowPrice;

    public BarBuilder(int openPrice, long openAt, long validTill) {
      this.openPrice = openPrice;
      this.closePrice = -1;
      this.highPrice = openPrice;
      this.lowPrice = openPrice;
      this.openAt = openAt;
      this.validTill = validTill;
    }

    public boolean isOpen(long now) {
      return now >= openAt && now < validTill;
    }

    public Bar build() {
      return new Bar(openPrice, closePrice, highPrice, lowPrice, openAt, validTill);
    }

    public void update(int price, long now) {
      if (!isOpen(now)) {
        throw new RuntimeException("bar closed");
      }
      if (price > highPrice) {
        highPrice = price;
      }
      if (price < lowPrice) {
        lowPrice = price;
      }
      closePrice = price;
    }

  }
}
