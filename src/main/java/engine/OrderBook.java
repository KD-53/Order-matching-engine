package engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A limit order book for a single instrument.
 *
 * <p>Each side keeps its price levels in a {@link PriorityQueue} so the best price is
 * always at the head (highest bid, lowest ask). Every price level is a
 * {@link Deque} of resting orders; the configured {@link MatchingPolicy} decides
 * whether the front (FIFO) or the back (LIFO) of the deque trades first.
 *
 * <p>The book is shared by many trader threads. All reads and writes of the
 * underlying collections happen inside {@code synchronized (lock)} blocks, so
 * matching an incoming order is atomic with respect to every other submission.
 */
public class OrderBook {
    private final MatchingPolicy policy;
    private final Object lock = new Object();

    private final PriorityQueue<Long> bidPrices = new PriorityQueue<>(Comparator.reverseOrder());
    private final PriorityQueue<Long> askPrices = new PriorityQueue<>();
    private final Map<Long, Deque<Order>> bidLevels = new HashMap<>();
    private final Map<Long, Deque<Order>> askLevels = new HashMap<>();
    private final List<Trade> trades = new ArrayList<>();

    public OrderBook(MatchingPolicy policy) {
        this.policy = policy;
    }

    public MatchingPolicy getPolicy() {
        return policy;
    }

    /**
     * Matches the incoming order against the opposite side, best price first, and
     * rests any unfilled remainder in the book.
     *
     * @return the trades produced by this order, in execution order
     */
    public List<Trade> submit(Order incoming) {
        List<Trade> executed = new ArrayList<>();
        synchronized (lock) {
            PriorityQueue<Long> oppositePrices = pricesFor(incoming.getSide().opposite());
            Map<Long, Deque<Order>> oppositeLevels = levelsFor(incoming.getSide().opposite());

            while (!incoming.isFilled() && !oppositePrices.isEmpty()) {
                long bestPrice = oppositePrices.peek();
                if (!incoming.crosses(bestPrice)) {
                    break;
                }
                Deque<Order> level = oppositeLevels.get(bestPrice);
                while (!incoming.isFilled() && !level.isEmpty()) {
                    Order resting = policy.next(level);
                    long qty = Math.min(incoming.getRemaining(), resting.getRemaining());
                    incoming.fill(qty);
                    resting.fill(qty);

                    Order buy = incoming.getSide() == Side.BUY ? incoming : resting;
                    Order sell = incoming.getSide() == Side.SELL ? incoming : resting;
                    Trade trade = new Trade(buy, sell, bestPrice, qty);
                    executed.add(trade);
                    trades.add(trade);

                    if (resting.isFilled()) {
                        policy.removeNext(level);
                    }
                }
                if (level.isEmpty()) {
                    oppositePrices.poll();
                    oppositeLevels.remove(bestPrice);
                }
            }

            if (!incoming.isFilled()) {
                rest(incoming);
            }
        }
        return executed;
    }

    private void rest(Order order) {
        Map<Long, Deque<Order>> levels = levelsFor(order.getSide());
        Deque<Order> level = levels.get(order.getPrice());
        if (level == null) {
            level = new ArrayDeque<>();
            levels.put(order.getPrice(), level);
            pricesFor(order.getSide()).offer(order.getPrice());
        }
        level.addLast(order);
    }

    private PriorityQueue<Long> pricesFor(Side side) {
        return side == Side.BUY ? bidPrices : askPrices;
    }

    private Map<Long, Deque<Order>> levelsFor(Side side) {
        return side == Side.BUY ? bidLevels : askLevels;
    }

    /** Highest resting bid price, or {@code null} if there are no bids. */
    public Long bestBid() {
        synchronized (lock) {
            return bidPrices.peek();
        }
    }

    /** Lowest resting ask price, or {@code null} if there are no asks. */
    public Long bestAsk() {
        synchronized (lock) {
            return askPrices.peek();
        }
    }

    /** Total unfilled quantity resting on one side of the book. */
    public long restingQuantity(Side side) {
        synchronized (lock) {
            long total = 0;
            for (Deque<Order> level : levelsFor(side).values()) {
                for (Order order : level) {
                    total += order.getRemaining();
                }
            }
            return total;
        }
    }

    /** Number of orders resting on one side of the book. */
    public int restingOrderCount(Side side) {
        synchronized (lock) {
            int count = 0;
            for (Deque<Order> level : levelsFor(side).values()) {
                count += level.size();
            }
            return count;
        }
    }

    /** A snapshot of every trade executed so far. */
    public List<Trade> getTrades() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(trades));
        }
    }
}
