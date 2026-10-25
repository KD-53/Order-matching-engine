package engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Self-checking tests for the order book (no external test framework required).
 * Exits with status 1 if any test fails.
 */
public class OrderBookTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        run("price priority: best ask trades first", OrderBookTest::bestAskTradesFirst);
        run("price priority: best bid trades first", OrderBookTest::bestBidTradesFirst);
        run("non-crossing orders rest in the book", OrderBookTest::nonCrossingOrdersRest);
        run("FIFO executes oldest order at a price level first", OrderBookTest::fifoWithinLevel);
        run("LIFO executes newest order at a price level first", OrderBookTest::lifoWithinLevel);
        run("partial fill of incoming order rests the remainder", OrderBookTest::partialFillIncoming);
        run("partial fill of resting order keeps it in the book", OrderBookTest::partialFillResting);
        run("incoming order sweeps multiple price levels", OrderBookTest::sweepsLevels);
        run("concurrent stress (FIFO): quantity is conserved", () -> concurrentStress(MatchingPolicy.FIFO));
        run("concurrent stress (LIFO): quantity is conserved", () -> concurrentStress(MatchingPolicy.LIFO));

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void bestAskTradesFirst() {
        OrderBook book = new OrderBook(MatchingPolicy.FIFO);
        book.submit(new Order("S1", Side.SELL, 102, 10));
        book.submit(new Order("S2", Side.SELL, 100, 10));
        book.submit(new Order("S3", Side.SELL, 101, 10));
        assertEquals(100L, book.bestAsk(), "best ask");

        List<Trade> trades = book.submit(new Order("B1", Side.BUY, 105, 10));
        assertEquals(1, trades.size(), "trade count");
        assertEquals(100L, trades.get(0).getPrice(), "trade price");
        assertEquals("S2", trades.get(0).getSellerId(), "seller");
        assertEquals(101L, book.bestAsk(), "next best ask");
    }

    static void bestBidTradesFirst() {
        OrderBook book = new OrderBook(MatchingPolicy.FIFO);
        book.submit(new Order("B1", Side.BUY, 98, 10));
        book.submit(new Order("B2", Side.BUY, 100, 10));
        book.submit(new Order("B3", Side.BUY, 99, 10));
        assertEquals(100L, book.bestBid(), "best bid");

        List<Trade> trades = book.submit(new Order("S1", Side.SELL, 95, 10));
        assertEquals(1, trades.size(), "trade count");
        assertEquals(100L, trades.get(0).getPrice(), "trade price");
        assertEquals("B2", trades.get(0).getBuyerId(), "buyer");
        assertEquals(99L, book.bestBid(), "next best bid");
    }

    static void nonCrossingOrdersRest() {
        OrderBook book = new OrderBook(MatchingPolicy.FIFO);
        book.submit(new Order("B1", Side.BUY, 99, 10));
        List<Trade> trades = book.submit(new Order("S1", Side.SELL, 100, 10));
        assertEquals(0, trades.size(), "trade count");
        assertEquals(99L, book.bestBid(), "best bid");
        assertEquals(100L, book.bestAsk(), "best ask");
    }

    static void fifoWithinLevel() {
        OrderBook book = new OrderBook(MatchingPolicy.FIFO);
        book.submit(new Order("first", Side.SELL, 100, 10));
        book.submit(new Order("second", Side.SELL, 100, 10));
        book.submit(new Order("third", Side.SELL, 100, 10));

        List<Trade> trades = book.submit(new Order("B", Side.BUY, 100, 15));
        assertEquals(2, trades.size(), "trade count");
        assertEquals("first", trades.get(0).getSellerId(), "first fill");
        assertEquals("second", trades.get(1).getSellerId(), "second fill");
        assertEquals(5L, trades.get(1).getQuantity(), "second fill qty");
    }

    static void lifoWithinLevel() {
        OrderBook book = new OrderBook(MatchingPolicy.LIFO);
        book.submit(new Order("first", Side.SELL, 100, 10));
        book.submit(new Order("second", Side.SELL, 100, 10));
        book.submit(new Order("third", Side.SELL, 100, 10));

        List<Trade> trades = book.submit(new Order("B", Side.BUY, 100, 15));
        assertEquals(2, trades.size(), "trade count");
        assertEquals("third", trades.get(0).getSellerId(), "first fill");
        assertEquals("second", trades.get(1).getSellerId(), "second fill");
        assertEquals(5L, trades.get(1).getQuantity(), "second fill qty");
    }

    static void partialFillIncoming() {
        OrderBook book = new OrderBook(MatchingPolicy.FIFO);
        book.submit(new Order("S", Side.SELL, 100, 30));
        Order buy = new Order("B", Side.BUY, 100, 50);
        List<Trade> trades = book.submit(buy);

        assertEquals(1, trades.size(), "trade count");
        assertEquals(30L, trades.get(0).getQuantity(), "fill qty");
        assertEquals(20L, buy.getRemaining(), "incoming remaining");
        assertEquals(null, book.bestAsk(), "ask side empty");
        assertEquals(100L, book.bestBid(), "remainder rests as bid");
        assertEquals(20L, book.restingQuantity(Side.BUY), "resting bid qty");
    }

    static void partialFillResting() {
        OrderBook book = new OrderBook(MatchingPolicy.FIFO);
        Order sell = new Order("S", Side.SELL, 100, 50);
        book.submit(sell);
        Order buy = new Order("B", Side.BUY, 100, 20);
        book.submit(buy);

        assertTrue(buy.isFilled(), "incoming fully filled");
        assertEquals(30L, sell.getRemaining(), "resting remaining");
        assertEquals(100L, book.bestAsk(), "resting order still at best ask");
        assertEquals(30L, book.restingQuantity(Side.SELL), "resting ask qty");
        assertEquals(null, book.bestBid(), "no bid rested");
    }

    static void sweepsLevels() {
        OrderBook book = new OrderBook(MatchingPolicy.FIFO);
        book.submit(new Order("S1", Side.SELL, 100, 10));
        book.submit(new Order("S2", Side.SELL, 101, 10));
        book.submit(new Order("S3", Side.SELL, 103, 10));

        List<Trade> trades = book.submit(new Order("B", Side.BUY, 102, 25));
        assertEquals(2, trades.size(), "trade count");
        assertEquals(100L, trades.get(0).getPrice(), "first level");
        assertEquals(101L, trades.get(1).getPrice(), "second level");
        assertEquals(102L, book.bestBid(), "remainder rests at its limit");
        assertEquals(5L, book.restingQuantity(Side.BUY), "resting bid qty");
        assertEquals(103L, book.bestAsk(), "untouched level remains");
    }

    static void concurrentStress(MatchingPolicy policy) throws InterruptedException {
        OrderBook book = new OrderBook(policy);
        int traderCount = 16;
        int ordersPerTrader = 20_000;
        List<Trader> traders = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < traderCount; i++) {
            Trader trader = new Trader("T" + i, book, ordersPerTrader, 1_000, 10, 50, 7L * i + 1);
            traders.add(trader);
            threads.add(new Thread(trader));
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        long buySubmitted = 0;
        long sellSubmitted = 0;
        Map<Long, Order> ordersById = new HashMap<>();
        for (Trader trader : traders) {
            assertEquals(ordersPerTrader, trader.getSubmittedOrders().size(), "orders per trader");
            for (Order order : trader.getSubmittedOrders()) {
                ordersById.put(order.getId(), order);
                if (order.getSide() == Side.BUY) {
                    buySubmitted += order.getQuantity();
                } else {
                    sellSubmitted += order.getQuantity();
                }
            }
        }

        long traded = 0;
        Map<Long, Long> filledPerOrder = new HashMap<>();
        for (Trade trade : book.getTrades()) {
            assertTrue(trade.getQuantity() > 0, "positive trade qty");
            Order buy = ordersById.get(trade.getBuyOrderId());
            Order sell = ordersById.get(trade.getSellOrderId());
            assertTrue(buy.getPrice() >= trade.getPrice() && sell.getPrice() <= trade.getPrice(),
                    "trade price within both limits");
            traded += trade.getQuantity();
            filledPerOrder.merge(trade.getBuyOrderId(), trade.getQuantity(), Long::sum);
            filledPerOrder.merge(trade.getSellOrderId(), trade.getQuantity(), Long::sum);
        }
        assertTrue(traded > 0, "some trades executed");

        assertEquals(buySubmitted, traded + book.restingQuantity(Side.BUY), "buy quantity conserved");
        assertEquals(sellSubmitted, traded + book.restingQuantity(Side.SELL), "sell quantity conserved");
        for (Order order : ordersById.values()) {
            assertTrue(order.getRemaining() >= 0, "non-negative remaining");
            assertEquals(order.getFilled(), filledPerOrder.getOrDefault(order.getId(), 0L),
                    "fills match trades for order " + order.getId());
        }

        Long bid = book.bestBid();
        Long ask = book.bestAsk();
        assertTrue(bid == null || ask == null || bid < ask, "book is not crossed: " + bid + " / " + ask);
    }

    // --- minimal assertion helpers ---

    interface TestBody {
        void run() throws Exception;
    }

    static void run(String name, TestBody body) {
        try {
            body.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("FAIL  " + name + " -> " + t.getMessage());
        }
    }

    static void assertEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    static void assertTrue(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
