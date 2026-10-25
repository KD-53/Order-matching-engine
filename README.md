# Concurrent Order Matching Engine

A stock exchange limit-order matching engine written in plain Java. Price levels live in priority queues and each level is a deque of resting orders, so execution within a level can be **FIFO** or **LIFO**. Many simulated traders run on their own threads (`Runnable`) and submit orders to one shared order book. That book is guarded by `synchronized` blocks, so concurrent submissions never race.

## Design

```
src/main/java/engine/
├── Order.java           limit order: id, trader, side, price (integer ticks), quantity, remaining
├── Trade.java           immutable execution record (buy/sell order ids, traders, price, qty)
├── OrderBook.java       price-time book + matching logic, protected by a synchronized lock
├── Trader.java          Runnable that submits random buy/sell limit orders to the shared book
├── MatchingEngine.java  main: starts N trader threads, joins them, prints a session summary
├── Side.java            BUY / SELL
└── MatchingPolicy.java  FIFO / LIFO tie-breaking within a price level
```

**Order book data structures**

| Structure | Purpose |
|---|---|
| `PriorityQueue<Long>` bids (reverse order) | head = best (highest) bid price |
| `PriorityQueue<Long>` asks (natural order) | head = best (lowest) ask price |
| `Map<Long, Deque<Order>>` per side | orders resting at each price level |
| `List<Trade>` | log of every execution |

**Matching**: an incoming order trades against the best opposite price for as long as its limit crosses. Within a level, `MatchingPolicy` picks the resting order. `FIFO` takes the front of the deque (oldest first) and `LIFO` takes the back (newest first). New resting orders always go on the back. A trade executes at the resting order's price. A level that empties is removed from both its queue and its map. Any quantity left unfilled rests in the book at its limit price.

**Concurrency**: all reads and writes of the book's collections happen inside `synchronized (lock)` on one private lock object. Matching one incoming order is therefore atomic: no other trader can see or change the book halfway through a match. An `Order`'s remaining quantity changes only under that lock. Order ids come from an `AtomicLong`.

**OOP**: each concept is its own class with a small responsibility. `Trader` knows only how to produce orders. `OrderBook` owns matching. `Trade` is an immutable value. Execution policy is a strategy enum (`MatchingPolicy`), so another tie-breaking rule needs a new enum constant and no change to the book.

## Build and run

Requires a JDK (tested with Java 19). No build tool or external libraries.

```bash
./build.sh          # Git Bash / Linux / macOS: compile + run tests
```

```powershell
.\build.ps1         # Windows PowerShell: compile + run tests
```

Run the simulation (after building):

```bash
java -cp out/main engine.MatchingEngine [FIFO|LIFO] [traders] [ordersPerTrader]
# defaults: FIFO 8 50000
```

## Tests

`src/test/java/engine/OrderBookTest.java` is a self-checking test runner. It needs no framework and exits non-zero on failure. It covers:

- price priority on both sides (best ask / best bid trade first)
- non-crossing orders resting in the book
- FIFO vs LIFO execution order within a price level
- partial fills of incoming and resting orders
- an incoming order sweeping several price levels
- a concurrent stress run (16 threads × 20,000 orders, both policies) checking:
  - quantity conservation: submitted = traded + resting, per side
  - each order's filled quantity matches its trades
  - trade prices stay within both limits
  - the final book is not crossed

```
PASS  price priority: best ask trades first
PASS  price priority: best bid trades first
PASS  non-crossing orders rest in the book
PASS  FIFO executes oldest order at a price level first
PASS  LIFO executes newest order at a price level first
PASS  partial fill of incoming order rests the remainder
PASS  partial fill of resting order keeps it in the book
PASS  incoming order sweeps multiple price levels
PASS  concurrent stress (FIFO): quantity is conserved
PASS  concurrent stress (LIFO): quantity is conserved

10 passed, 0 failed
```

## Sample output

```
$ java -cp out/main engine.MatchingEngine FIFO
Policy: FIFO | traders: 8 | orders per trader: 50,000
Elapsed: 0.299 s | throughput: 1,336,674 orders/s
Trades: 310,948 | traded volume: 7,949,513
Best bid: 9977 | best ask: 9983
Resting bids: 43,145 orders / 2,177,703 qty | resting asks: 42,615 orders / 2,153,144 qty
Sample trades:
  Trade[26 @ 10009, buy #1 (T1) <- sell #4 (T1)]
  Trade[20 @ 10009, buy #1 (T1) <- sell #11 (T1)]
  Trade[3 @ 10009, buy #1 (T1) <- sell #14 (T1)]
  Trade[2 @ 9997, buy #2 (T8) <- sell #14 (T1)]
  Trade[68 @ 9976, buy #10 (T2) <- sell #14 (T1)]
Quantity conservation: buy 10,127,216 = 7,949,513 traded + 2,177,703 resting [OK]; sell 10,102,657 = 7,949,513 traded + 2,153,144 resting [OK]
```

Prices are integer ticks (for example cents around a mid price of 10,000). Throughput depends on the machine. Thread scheduling changes which trades happen from run to run, but quantity is conserved every time.
