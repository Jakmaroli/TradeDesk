package com.tradedesk.ui;

import javafx.beans.property.*;

/**
 * JavaFX row model for the live ticker TableView.
 * Uses JavaFX Properties so the table reacts to changes.
 * Owner: Member 1 (Jak)
 */
public class TickerRow {

    private final StringProperty  ticker  = new SimpleStringProperty();
    private final StringProperty  company = new SimpleStringProperty();
    private final StringProperty  sector  = new SimpleStringProperty();
    private final StringProperty  price   = new SimpleStringProperty();
    private final StringProperty  change  = new SimpleStringProperty();

    public TickerRow(String ticker, String company, String sector, double price, double changePct) {
        this.ticker.set(ticker);
        this.company.set(company);
        this.sector.set(sector);
        this.price.set(String.format("₹%.2f", price));
        this.change.set(String.format("%+.2f%%", changePct));
    }

    public void updatePrice(double newPrice, double changePct) {
        this.price.set(String.format("₹%.2f", newPrice));
        this.change.set(String.format("%+.2f%%", changePct));
    }

    // JavaFX property accessors (required by PropertyValueFactory)
    public String getTicker()  { return ticker.get(); }
    public String getCompany() { return company.get(); }
    public String getSector()  { return sector.get(); }
    public String getPrice()   { return price.get(); }
    public String getChange()  { return change.get(); }

    public StringProperty tickerProperty()  { return ticker; }
    public StringProperty companyProperty() { return company; }
    public StringProperty sectorProperty()  { return sector; }
    public StringProperty priceProperty()   { return price; }
    public StringProperty changeProperty()  { return change; }
}
