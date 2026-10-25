package engine;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A limit order. Prices are integer ticks (e.g. cents) to avoid floating-point
 * comparison problems. The remaining quantity is only mutated by the
 * {@link OrderBook} while it holds its lock.
 */
public class Order {
    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private final long id;
    private final String traderId;
    private final Side side;
    private final long price;
    private final long quantity;
    private long remaining;

    public Order(String traderId, Side side, long price, long quantity) {
        if (price <= 0) {
            throw new IllegalArgumentException("price must be positive: " + price);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        this.id = NEXT_ID.getAndIncrement();
        this.traderId = traderId;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.remaining = quantity;
    }

    public long getId() {
        return id;
    }

    public String getTraderId() {
        return traderId;
    }

    public Side getSide() {
        return side;
    }

    public long getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getRemaining() {
        return remaining;
    }

    public long getFilled() {
        return quantity - remaining;
    }

    public boolean isFilled() {
        return remaining == 0;
    }

    /** Reduces the remaining quantity; called only by the order book under its lock. */
    void fill(long qty) {
        if (qty <= 0 || qty > remaining) {
            throw new IllegalStateException("invalid fill " + qty + " for remaining " + remaining);
        }
        remaining -= qty;
    }

    /** True if this order's limit price is acceptable against a resting order's price. */
    boolean crosses(long restingPrice) {
        return side == Side.BUY ? price >= restingPrice : price <= restingPrice;
    }

    @Override
    public String toString() {
        return String.format("Order#%d[%s %s %d/%d @ %d]",
                id, traderId, side, remaining, quantity, price);
    }
}
