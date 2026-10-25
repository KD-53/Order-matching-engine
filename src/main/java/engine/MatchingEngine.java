package engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point: runs several {@link Trader} threads concurrently against one shared
 * {@link OrderBook} and prints a summary of the session.
 *
 * <p>Usage: {@code java -cp out/main engine.MatchingEngine [FIFO|LIFO] [traders] [ordersPerTrader]}
 */
public class MatchingEngine {

    public static void main(String[] args) throws InterruptedException {
        MatchingPolicy policy = args.length > 0 ? MatchingPolicy.valueOf(args[0].toUpperCase()) : MatchingPolicy.FIFO;
        int traderCount = args.length > 1 ? Integer.parseInt(args[1]) : 8;
        int ordersPerTrader = args.length > 2 ? Integer.parseInt(args[2]) : 50_000;

        OrderBook book = new OrderBook(policy);
        List<Trader> traders = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < traderCount; i++) {
            Trader trader = new Trader("T" + (i + 1), book, ordersPerTrader, 10_000, 50, 100, 42L + i);
            traders.add(trader);
            threads.add(new Thread(trader, trader.getId()));
        }

        System.out.printf("Policy: %s | traders: %d | orders per trader: %,d%n",
                policy, traderCount, ordersPerTrader);

        long start = System.nanoTime();
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        double seconds = (System.nanoTime() - start) / 1e9;

        long totalOrders = (long) traderCount * ordersPerTrader;
        long buySubmitted = 0;
        long sellSubmitted = 0;
        for (Trader trader : traders) {
            for (Order order : trader.getSubmittedOrders()) {
                if (order.getSide() == Side.BUY) {
                    buySubmitted += order.getQuantity();
                } else {
                    sellSubmitted += order.getQuantity();
                }
            }
        }

        List<Trade> trades = book.getTrades();
        long tradedVolume = 0;
        for (Trade trade : trades) {
            tradedVolume += trade.getQuantity();
        }
        long restingBids = book.restingQuantity(Side.BUY);
        long restingAsks = book.restingQuantity(Side.SELL);

        System.out.printf("Elapsed: %.3f s | throughput: %,.0f orders/s%n", seconds, totalOrders / seconds);
        System.out.printf("Trades: %,d | traded volume: %,d%n", trades.size(), tradedVolume);
        System.out.printf("Best bid: %s | best ask: %s%n", book.bestBid(), book.bestAsk());
        System.out.printf("Resting bids: %,d orders / %,d qty | resting asks: %,d orders / %,d qty%n",
                book.restingOrderCount(Side.BUY), restingBids,
                book.restingOrderCount(Side.SELL), restingAsks);

        System.out.println("Sample trades:");
        for (int i = 0; i < Math.min(5, trades.size()); i++) {
            System.out.println("  " + trades.get(i));
        }

        boolean buyOk = buySubmitted == tradedVolume + restingBids;
        boolean sellOk = sellSubmitted == tradedVolume + restingAsks;
        System.out.printf("Quantity conservation: buy %,d = %,d traded + %,d resting [%s]; "
                        + "sell %,d = %,d traded + %,d resting [%s]%n",
                buySubmitted, tradedVolume, restingBids, buyOk ? "OK" : "FAIL",
                sellSubmitted, tradedVolume, restingAsks, sellOk ? "OK" : "FAIL");
        if (!buyOk || !sellOk) {
            System.exit(1);
        }
    }
}
