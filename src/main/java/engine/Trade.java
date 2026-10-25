package engine;

/** An immutable record of an execution between a buy order and a sell order. */
public final class Trade {
    private final long buyOrderId;
    private final long sellOrderId;
    private final String buyerId;
    private final String sellerId;
    private final long price;
    private final long quantity;

    public Trade(Order buy, Order sell, long price, long quantity) {
        this.buyOrderId = buy.getId();
        this.sellOrderId = sell.getId();
        this.buyerId = buy.getTraderId();
        this.sellerId = sell.getTraderId();
        this.price = price;
        this.quantity = quantity;
    }

    public long getBuyOrderId() {
        return buyOrderId;
    }

    public long getSellOrderId() {
        return sellOrderId;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public long getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    @Override
    public String toString() {
        return String.format("Trade[%d @ %d, buy #%d (%s) <- sell #%d (%s)]",
                quantity, price, buyOrderId, buyerId, sellOrderId, sellerId);
    }
}
