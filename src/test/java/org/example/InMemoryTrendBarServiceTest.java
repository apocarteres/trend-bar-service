package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InMemoryTrendBarServiceTest {

  @Mock
  private Supplier<Long> timeSupplier;

  private InMemoryTrendBarService service;

  @BeforeEach
  void setUp() {
    when(timeSupplier.get()).thenReturn(1000L);
    service = new InMemoryTrendBarService(timeSupplier);
  }

  @Test
  void noHistoryIfNoUpdates() {
    assertEquals(0, service.history(Symbol.EURJPY, Period.M1, 0).size());
  }

  @Test
  void noHistoryIfNoCommittedBars() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.updateHistory();
    assertEquals(0, service.history(Symbol.EURJPY, Period.M1, 0).size());
  }

  @Test
  void noHistoryIfNoCommittedBarsForSymbol() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 310, 1010);
    service.feed(Symbol.EURJPY, 270, 1070);
    service.updateHistory();
    assertEquals(0, service.history(Symbol.EURUSD, Period.M1, 0).size());
  }

  @Test
  void openAtTimeIsTimeOfFirstQuote() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 310, 1010);
    service.feed(Symbol.EURJPY, 270, 1070);
    service.updateHistory();
    var history = service.history(Symbol.EURJPY, Period.M1, 900, 1500);
    assertEquals(1000, history.getFirst().openAt());
  }

  @Test
  void closedAtTimeIsStartPlusPeriod() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 310, 1010);
    service.feed(Symbol.EURJPY, 270, 1070);
    service.updateHistory();
    var history = service.history(Symbol.EURJPY, Period.M1, 900, 1500);
    assertEquals(1060, history.getFirst().closedAt());
  }

  @Test
  void openPriceIsFirstQuotePrice() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 310, 1010);
    service.feed(Symbol.EURJPY, 270, 1070);
    service.updateHistory();
    var history = service.history(Symbol.EURJPY, Period.M1, 900, 1500);
    assertEquals(300, history.getFirst().openPrice());
  }

  @Test
  void closePriceIsLastQuotePrice() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 310, 1010);
    service.feed(Symbol.EURJPY, 270, 1060);
    service.feed(Symbol.EURJPY, 230, 1061);
    service.updateHistory();
    var history = service.history(Symbol.EURJPY, Period.M1, 900, 1500);
    assertEquals(270, history.getFirst().closePrice());
  }

  @Test
  void lowPriceIsLowestPriceWithinPeriod() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 310, 1010);
    service.feed(Symbol.EURJPY, 270, 1058);
    service.feed(Symbol.EURJPY, 210, 1059);
    service.feed(Symbol.EURJPY, 280, 1060);
    service.feed(Symbol.EURJPY, 230, 1061);
    service.updateHistory();
    var history = service.history(Symbol.EURJPY, Period.M1, 900, 1500);
    assertEquals(210, history.getFirst().lowPrice());
  }

  @Test
  void highPriceIsHighestPriceWithinPeriod() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 380, 1010);
    service.feed(Symbol.EURJPY, 270, 1058);
    service.feed(Symbol.EURJPY, 210, 1059);
    service.feed(Symbol.EURJPY, 280, 1060);
    service.feed(Symbol.EURJPY, 230, 1061);
    service.updateHistory();
    var history = service.history(Symbol.EURJPY, Period.M1, 900, 1500);
    assertEquals(380, history.getFirst().highPrice());
  }

  @Test
    //not sure should we include closed bars anyways here??
  void noBarsInRangeWhichSmallerThanPeriod() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 310, 1010);
    service.feed(Symbol.EURJPY, 270, 1058);
    service.feed(Symbol.EURJPY, 210, 1059);
    service.feed(Symbol.EURJPY, 280, 1060);

    service.feed(Symbol.EURJPY, 400, 1061);
    service.feed(Symbol.EURJPY, 410, 1070);
    service.feed(Symbol.EURJPY, 470, 1080);
    service.feed(Symbol.EURJPY, 310, 1090);
    service.feed(Symbol.EURJPY, 380, 1121);
    service.updateHistory();
    var history = service.history(Symbol.EURJPY, Period.M1, 1050, 1080);
    assertEquals(0, history.size());
  }

  @Test
  void twoCommitedBarsIfMatchRange() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 310, 1010);
    service.feed(Symbol.EURJPY, 270, 1058);
    service.feed(Symbol.EURJPY, 210, 1059);
    service.feed(Symbol.EURJPY, 280, 1060);

    service.feed(Symbol.EURJPY, 400, 1061);
    service.feed(Symbol.EURJPY, 410, 1070);
    service.feed(Symbol.EURJPY, 470, 1080);
    service.feed(Symbol.EURJPY, 310, 1090);
    service.feed(Symbol.EURJPY, 380, 1121);
    service.updateHistory();
    var history = service.history(Symbol.EURJPY, Period.M1, 900, 1200);
    assertEquals(2, history.size());
  }

  // here is a guess, should we include first bar, because
  // it closed already and closed after "from" time. It is not actually clear
  // from task description how we should handle such case.
  @Test
  void twoCommitedBarsIfMatchRangeBeforeClose() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 310, 1010);
    service.feed(Symbol.EURJPY, 270, 1058);
    service.feed(Symbol.EURJPY, 210, 1059);
    service.feed(Symbol.EURJPY, 280, 1060);

    service.feed(Symbol.EURJPY, 400, 1061);
    service.feed(Symbol.EURJPY, 410, 1070);
    service.feed(Symbol.EURJPY, 470, 1080);
    service.feed(Symbol.EURJPY, 310, 1090);
    service.feed(Symbol.EURJPY, 380, 1121);
    service.updateHistory();
    var history = service.history(Symbol.EURJPY, Period.M1, 1058, 1200);
    assertEquals(1, history.size());
  }

  @Test
  void noBarsIfOutRangePartialQuotes() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 310, 1010);
    service.feed(Symbol.EURJPY, 270, 1058);
    service.feed(Symbol.EURJPY, 210, 1059);
    service.feed(Symbol.EURJPY, 280, 1060);

    service.feed(Symbol.EURJPY, 400, 1061);
    service.feed(Symbol.EURJPY, 410, 1070);
    service.feed(Symbol.EURJPY, 470, 1080);
    service.feed(Symbol.EURJPY, 310, 1090);
    service.feed(Symbol.EURJPY, 380, 1121);
    service.updateHistory();
    var history = service.history(Symbol.EURJPY, Period.M1, 900, 1058);
    assertEquals(0, history.size());
  }

  @Test
  void noBarsIfOutRangeAllQuotes() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 310, 1010);
    service.feed(Symbol.EURJPY, 270, 1058);
    service.feed(Symbol.EURJPY, 210, 1059);
    service.feed(Symbol.EURJPY, 280, 1060);

    service.feed(Symbol.EURJPY, 400, 1061);
    service.feed(Symbol.EURJPY, 410, 1070);
    service.feed(Symbol.EURJPY, 470, 1080);
    service.feed(Symbol.EURJPY, 310, 1090);
    service.feed(Symbol.EURJPY, 380, 1121);
    service.updateHistory();
    var history = service.history(Symbol.EURJPY, Period.M1, 1200, 1300);
    assertEquals(0, history.size());
  }

  @Test
  void commitsBarWhenTimeIsUp() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 310, 1010);
    service.feed(Symbol.EURJPY, 270, 1058);
    service.feed(Symbol.EURJPY, 210, 1059);
    when(timeSupplier.get()).thenReturn(1200L);
    service.updateHistory();
    var history = service.history(Symbol.EURJPY, Period.M1, 1000, 1500);
    assertEquals(1, history.size());
  }

  @Test
  void nextBarCreatedAfterCommitFirstOne() {
    service.feed(Symbol.EURJPY, 300, 1000);
    service.feed(Symbol.EURJPY, 310, 1010);
    service.feed(Symbol.EURJPY, 270, 1058);
    service.feed(Symbol.EURJPY, 210, 1059);
    when(timeSupplier.get()).thenReturn(1200L);
    service.updateHistory();
    service.feed(Symbol.EURJPY, 400, 1250);
    service.feed(Symbol.EURJPY, 401, 1251);
    service.feed(Symbol.EURJPY, 402, 1252);
    service.feed(Symbol.EURJPY, 403, 1253);
    when(timeSupplier.get()).thenReturn(1400L);
    service.updateHistory();
    var history = service.history(Symbol.EURJPY, Period.M1, 1000, 1500);
    assertEquals(2, history.size());
  }

  @Test
  void commitingPeriodDoesNotAffectOtherPeriod() {
    service.feed(Symbol.EURJPY, 100, 1000);
    service.feed(Symbol.EURJPY, 100, 4551);
    service.feed(Symbol.EURJPY, 200, 4552);
    service.feed(Symbol.EURJPY, 100, 4553);
    when(timeSupplier.get()).thenReturn(4601L);
    service.updateHistory();
    var m1 = service.history(Symbol.EURJPY, Period.M1, 4500, 4700);
    var h1 = service.history(Symbol.EURJPY, Period.H1, 1000, 5000);
    assertEquals(0, m1.size());
    assertEquals(200, h1.getFirst().highPrice());
  }

}