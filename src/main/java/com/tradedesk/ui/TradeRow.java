package com.tradedesk.ui;

import com.tradedesk.model.Order;
import javafx.beans.property.*;

import java.time.format.DateTimeFormatter;

/**
 * JavaFX row model for the trade history TableView.
 * Also provides toCsvLine() for CSV export.
 * Owner: Member 1 (Jak)
 */
public class TradeRow {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringProperty orderId = new SimpleStringProperty();
    private final StringProperty side    = new SimpleStringProperty();
    private final StringProperty ticker  = new SimpleStringProperty();
    private final StringProperty qty     = new SimpleStringProperty();
    private final StringProperty price   = new SimpleStringProperty();
    private final StringProperty total   = new SimpleStringProperty();
    private final StringProperty status  = new SimpleStringProperty();
    private final StringProperty time    = new SimpleStringProperty();

    public TradeRow(Order order, double executedPrice) {
        boolean isBuy = !order.getUsername().startsWith("SELL:");
        this.orderId.set(order.getOrderId());
        this.side.set(isBuy ? "BUY" : "SELL");
        this.ticker.set(order.getTicker());
        this.qty.set(String.valueOf(order.getQuantity()));
        this.price.set(String.format("₹%.2f", executedPrice));
        this.total.set(String.format("₹%.2f", executedPrice * order.getQuantity()));
        this.status.set(order.getStatus().name());
        this.time.set(order.getCreatedAt().format(FMT));
    }

    public String getOrderId() { return orderId.get(); }
    public String getSide()    { return side.get(); }
    public String getTicker()  { return ticker.get(); }
    public String getQty()     { return qty.get(); }
    public String getPrice()   { return price.get(); }
    public String getTotal()   { return total.get(); }
    public String getStatus()  { return status.get(); }
    public String getTime()    { return time.get(); }

    public String toCsvLine() {
        return String.join(",",
                getOrderId(), getSide(), getTicker(), getQty(),
                getPrice().replace("₹",""),
                getTotal().replace("₹",""),
                getStatus(), getTime());
    }
}
