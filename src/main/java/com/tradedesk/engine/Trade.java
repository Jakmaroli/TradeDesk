package com.tradedesk.engine;

import com.tradedesk.model.Order;

import java.time.LocalDateTime;

/**
 * Immutable record of a matched trade.
 * Owner: Member 1 (Jak)
 */
public class Trade {

    private final Order  buyOrder;
    private final Order  sellOrder;
    private final double executedPrice;
    private final int    quantity;
    private final LocalDateTime executedAt;

    public Trade(Order buyOrder, Order sellOrder, double executedPrice) {
        this.buyOrder      = buyOrder;
        this.sellOrder     = sellOrder;
        this.executedPrice = executedPrice;
        this.quantity      = Math.min(buyOrder.getQuantity(), sellOrder.getQuantity());
        this.executedAt    = LocalDateTime.now();
    }

    public Order  getBuyOrder()      { return buyOrder; }
    public Order  getSellOrder()     { return sellOrder; }
    public double getExecutedPrice() { return executedPrice; }
    public int    getQuantity()      { return quantity; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public double getTurnover()      { return executedPrice * quantity; }

    public String getBuyer()  { return buyOrder.getUsername(); }
    public String getSeller() { return sellOrder.getUsername(); }
    public String getTicker() { return buyOrder.getTicker(); }

    @Override
    public String toString() {
        return String.format("[TRADE] %s × %d @ ₹%.2f | %s ← %s",
                getTicker(), quantity, executedPrice, getBuyer(), getSeller());
    }
}
