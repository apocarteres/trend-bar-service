package org.example;

import java.util.List;

public interface TrendBarService {
  List<Bar> history(Symbol symbol, Period period, long from);

  List<Bar> history(Symbol symbol, Period period, long from, long to);

  void feed(Symbol symbol, int price, long timestamp);
}
