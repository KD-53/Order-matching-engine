package engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A simulated trader that submits random buy/sell limit orders around a mid price
 * to a shared {@link OrderBook}. Each trader runs on its own thread.
 */
public class Trader implements Runnable {
    private final String id;
    private final OrderBook book;
    private final int orderCount;
    private final long midPrice;
    private final int priceSpread;
    private final int maxQuantity;
    private final Random random;

    private final List<Order> submitted = new ArrayList<>();
    private long tradesExecuted;

    public Trader(String id, OrderBook book, int orderCount, long midPrice,
                  int priceSpread, int maxQuantity, long seed) {
        this.id = id;
        this.book = book;
        this.orderCount = orderCount;
        this.midPrice = midPrice;
        this.priceSpread = priceSpread;
        this.maxQuantity = maxQuantity;
        this.random = new Random(seed);
    }

    @Override
    public void run() {
        for (int i = 0; i < orderCount; i++) {
            Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            long price = midPrice + random.nextInt(2 * priceSpread + 1) - priceSpread;
            long quantity = 1 + random.nextInt(maxQuantity);
            Order order = new Order(id, side, price, quantity);
            submitted.add(order);
            tradesExecuted += book.submit(order).size();
        }
    }

    public String getId() {
        return id;
    }

    /** Orders this trader submitted; read only after the trader's thread has finished. */
    public List<Order> getSubmittedOrders() {
        return Collections.unmodifiableList(submitted);
    }

    /** Trades triggered by this trader's incoming orders (it was the aggressor). */
    public long getTradesExecuted() {
        return tradesExecuted;
    }
}
