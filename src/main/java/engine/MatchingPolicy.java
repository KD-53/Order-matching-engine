package engine;

import java.util.Deque;

/**
 * Decides which resting order at a single price level trades first.
 * Price priority is always enforced by the order book; this only breaks ties
 * between orders resting at the same price.
 */
public enum MatchingPolicy {
    /** First in, first out: the oldest order at the price level trades first. */
    FIFO {
        @Override
        Order next(Deque<Order> level) {
            return level.peekFirst();
        }

        @Override
        void removeNext(Deque<Order> level) {
            level.pollFirst();
        }
    },

    /** Last in, first out: the newest order at the price level trades first. */
    LIFO {
        @Override
        Order next(Deque<Order> level) {
            return level.peekLast();
        }

        @Override
        void removeNext(Deque<Order> level) {
            level.pollLast();
        }
    };

    abstract Order next(Deque<Order> level);

    abstract void removeNext(Deque<Order> level);
}
