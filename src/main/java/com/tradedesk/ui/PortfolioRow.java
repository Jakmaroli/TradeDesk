package com.tradedesk.ui;

import javafx.beans.property.*;

/**
 * JavaFX row model for the portfolio TableView.
 * Owner: Member 1 (Jak)
 */
public class PortfolioRow {

    private final StringProperty ticker   = new SimpleStringProperty();
    private final StringProperty qty      = new SimpleStringProperty();
    private final StringProperty avgPrice = new SimpleStringProperty();
    private final StringProperty currPrice= new SimpleStringProperty();
    private final StringProperty pnl      = new SimpleStringProperty();

    public PortfolioRow(String ticker, int qty, double avg, double curr, double pnlValue) {
        this.ticker.set(ticker);
        this.qty.set(String.valueOf(qty));
        this.avgPrice.set(String.format("₹%.2f", avg));
        this.currPrice.set(String.format("₹%.2f", curr));
        this.pnl.set(String.format("%s₹%.2f", pnlValue >= 0 ? "+" : "", pnlValue));
    }

    public String getTicker()   { return ticker.get(); }
    public String getQty()      { return qty.get(); }
    public String getAvgPrice() { return avgPrice.get(); }
    public String getCurrPrice(){ return currPrice.get(); }
    public String getPnl()      { return pnl.get(); }
}
