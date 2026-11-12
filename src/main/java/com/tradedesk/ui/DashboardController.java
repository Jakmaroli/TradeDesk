package com.tradedesk.ui;

import com.tradedesk.engine.*;
import com.tradedesk.model.*;
import javafx.application.Platform;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Main trading dashboard — MVC Controller.
 * Implements TradeObserver to receive live updates from the engine.
 *
 * Layout:
 *   ┌────┬───────────────────────────────────────────────────────┐
 *   │    │  HEADER: logo | user | cash | total P&L [Leaderboard] │
 *   │ S  ├─────────────────┬─────────────────────────────────────┤
 *   │ I  │  LIVE MARKET    │  NEW ORDER                          │
 *   │ D  │  ticker table   │  Stock | Qty | Type | [BUY] [SELL]  │
 *   │ E  │  + sparkline    ├─────────────────────────────────────┤
 *   │ B  │                 │  MY PORTFOLIO                       │
 *   │ A  ├─────────────────┴─────────────────────────────────────┤
 *   │ R  │  TRADE HISTORY  [Export CSV]                          │
 *   ├────┴───────────────────────────────────────────────────────┤
 *   │  STATUS BAR: connection • last update • trades today       │
 *   └───────────────────────────────────────────────────────────┘
 *
 * Owner: Member 1 (Jak)
 */
public class DashboardController implements TradeObserver {

    private final Stage  stage;
    private final String username;
    private final MatchingEngine engine = TradeDeskApp.ENGINE;

    private final ObservableList<TickerRow>    tickerRows    = FXCollections.observableArrayList();
    private final ObservableList<PortfolioRow> portfolioRows = FXCollections.observableArrayList();
    private final ObservableList<TradeRow>     tradeRows     = FXCollections.observableArrayList();

    private Label cashLabel;
    private Label pnlLabel;
    private Label orderFeedback;
    private TextField orderTickerField;

    private Label statusConnectionLabel;
    private Region statusDot;
    private Label statusUpdateLabel;
    private Label statusTradesLabel;
    private int tradesToday = 0;

    private final Map<String, List<Double>> priceHistory = new HashMap<>();
    private static final int SPARKLINE_POINTS = 20;

    public DashboardController(Stage stage, String username) {
        this.stage    = stage;
        this.username = username;
        engine.addObserver(this);
        engine.getOrCreatePortfolio(username);
    }

    public void show() {
        stage.setTitle("TradeDesk — " + username);

        BorderPane root = new BorderPane();
        root.setLeft(buildSidebar());

        BorderPane mainArea = new BorderPane();
        mainArea.setTop(buildHeader());
        mainArea.setCenter(buildCenter());
        mainArea.setBottom(buildTradeHistory());
        mainArea.setStyle("-fx-background-color: #0d1117;");

        root.setCenter(mainArea);
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(
                getClass().getResource("/com/tradedesk/ui/dark-theme.css").toExternalForm());

        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.show();

        populateTickerRows();
        refreshPortfolio();
        updateStatusBar();
    }

    // ── Sidebar ────────────────────────────────────────────────────────────
    private VBox buildSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setAlignment(Pos.TOP_CENTER);

        Button marketBtn   = navButton("📊", "Market");
        Button portfolioBtn= navButton("💼", "Portfolio");
        Button historyBtn  = navButton("📜", "History");
        Button settingsBtn = navButton("⚙", "Settings");

        marketBtn.getStyleClass().add("active-nav");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(marketBtn, portfolioBtn, historyBtn, spacer, settingsBtn);
        return sidebar;
    }

    private Button navButton(String icon, String label) {
        Button btn = new Button(label);
        btn.setGraphic(new Label(icon) {{
            setStyle("-fx-font-size: 18px;");
        }});
        return btn;
    }

    // ── Header ─────────────────────────────────────────────────────────────
    private HBox buildHeader() {
        HBox header = new HBox(20);
        header.setPadding(new Insets(14, 20, 14, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #161b22; -fx-border-color: #30363d; " +
                "-fx-border-width: 0 0 1 0;");

        Text logo = new Text("📈 TradeDesk");
        logo.setFont(Font.font("Monospace", FontWeight.BOLD, 20));
        logo.setFill(Color.web("#00d4aa"));

        Label userLbl = new Label("👤 " + username);
        userLbl.getStyleClass().add("muted");

        cashLabel = new Label("Cash: ₹10,00,000.00");
        cashLabel.setTextFill(Color.web("#58a6ff"));
        cashLabel.setFont(Font.font(null, FontWeight.BOLD, 13));

        pnlLabel = new Label("P&L: +₹0.00");
        pnlLabel.setTextFill(Color.web("#3fb950"));
        pnlLabel.setFont(Font.font(null, FontWeight.BOLD, 13));

        Label liveDot = new Label("● LIVE");
        liveDot.setTextFill(Color.web("#00d4aa"));
        liveDot.setFont(Font.font(null, FontWeight.BOLD, 12));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button leaderBtn = new Button("🏆 Leaderboard");
        leaderBtn.setOnAction(e -> showLeaderboard());

        header.getChildren().addAll(logo, userLbl, cashLabel, pnlLabel, spacer, liveDot, leaderBtn);
        return header;
    }

    // ── Centre ──────────────────────────────────────────────────────────────
    private SplitPane buildCenter() {
        SplitPane split = new SplitPane(buildTickerPanel(), buildRightPanel());
        split.setDividerPositions(0.56);
        return split;
    }

    // ── Live Market Table ───────────────────────────────────────────────────
    private VBox buildTickerPanel() {
        Label heading = sectionHeading("Live Market");

        TableView<TickerRow> table = new TableView<>(tickerRows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Loading prices…"));

        TableColumn<TickerRow, String> tickerCol  = strCol("Ticker",  "ticker",  100);
        TableColumn<TickerRow, String> companyCol = strCol("Company", "company", 170);
        TableColumn<TickerRow, String> sectorCol  = strCol("Sector",  "sector",  130);
        TableColumn<TickerRow, String> priceCol   = strCol("Price ₹", "price",    90);

        TableColumn<TickerRow, String> changeCol = new TableColumn<>("Change");
        changeCol.setCellValueFactory(new PropertyValueFactory<>("change"));
        changeCol.setCellFactory(colourChangeCellWithBorder());
        changeCol.setPrefWidth(90);

        TableColumn<TickerRow, String> sparkCol = new TableColumn<>("Trend");
        sparkCol.setCellValueFactory(new PropertyValueFactory<>("ticker"));
        sparkCol.setCellFactory(sparklineCell());
        sparkCol.setPrefWidth(80);
        sparkCol.setSortable(false);

        table.getColumns().addAll(tickerCol, companyCol, sectorCol, priceCol, changeCol, sparkCol);

        table.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, row) -> { if (row != null) orderTickerField.setText(row.getTicker()); });

        VBox.setVgrow(table, Priority.ALWAYS);
        VBox panel = new VBox(0, heading, table);
        return panel;
    }

    // ── Right panel: Order Form + Portfolio ─────────────────────────────────
    private VBox buildRightPanel() {
        VBox panel = new VBox(0, buildOrderForm(), buildPortfolioPanel());
        return panel;
    }

    private VBox buildOrderForm() {
        Label heading = sectionHeading("New Order");

        orderTickerField = new TextField();
        orderTickerField.setPromptText("e.g. RELIANCE");

        TextField qtyField   = new TextField("1");
        TextField limitField = new TextField();
        limitField.setPromptText("Leave blank for market order");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(0, 16, 12, 16));

        grid.add(formLbl("Ticker:"),      0, 0); grid.add(orderTickerField, 1, 0);
        grid.add(formLbl("Quantity:"),    0, 1); grid.add(qtyField,         1, 1);
        grid.add(formLbl("Limit Price:"), 0, 2); grid.add(limitField,       1, 2);
        GridPane.setFillWidth(orderTickerField, true);
        GridPane.setFillWidth(qtyField,         true);
        GridPane.setFillWidth(limitField,       true);
        grid.setMaxWidth(Double.MAX_VALUE);

        Button buyBtn  = new Button("▲  BUY");
        Button sellBtn = new Button("▼  SELL");
        buyBtn.getStyleClass().add("buy-btn");
        sellBtn.getStyleClass().add("sell-btn");
        buyBtn.setMaxWidth(Double.MAX_VALUE);
        sellBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(buyBtn, Priority.ALWAYS);
        HBox.setHgrow(sellBtn, Priority.ALWAYS);

        buyBtn .setOnAction(e -> placeOrder(orderTickerField, qtyField, limitField, true));
        sellBtn.setOnAction(e -> placeOrder(orderTickerField, qtyField, limitField, false));

        HBox btnRow = new HBox(12, buyBtn, sellBtn);
        btnRow.setPadding(new Insets(4, 16, 12, 16));

        orderFeedback = new Label(" ");
        orderFeedback.getStyleClass().add("muted");
        orderFeedback.setWrapText(true);
        orderFeedback.setPadding(new Insets(0, 16, 8, 16));

        VBox form = new VBox(0, heading, grid, btnRow, orderFeedback);
        form.setStyle("-fx-border-color: #30363d; -fx-border-width: 0 0 1 0;");
        return form;
    }

    private VBox buildPortfolioPanel() {
        Label heading = sectionHeading("My Portfolio");

        TableView<PortfolioRow> table = new TableView<>(portfolioRows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No holdings yet"));

        TableColumn<PortfolioRow, String> pnlCol = new TableColumn<>("Unrealised P&L");
        pnlCol.setCellValueFactory(new PropertyValueFactory<>("pnl"));
        pnlCol.setCellFactory(colourPnlCell());
        pnlCol.setPrefWidth(130);

        table.getColumns().addAll(
                strCol("Ticker",    "ticker",    80),
                strCol("Qty",       "qty",        50),
                strCol("Avg Buy ₹", "avgPrice",   95),
                strCol("Current ₹", "currPrice",  95),
                pnlCol
        );

        VBox.setVgrow(table, Priority.ALWAYS);
        VBox panel = new VBox(0, heading, table);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    // ── Trade History ───────────────────────────────────────────────────────
    private VBox buildTradeHistory() {
        Label heading = sectionHeading("Trade History");

        Button exportBtn = new Button("⬇ Export CSV");
        exportBtn.setOnAction(e -> exportCSV());

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox topRow = new HBox(heading, sp, exportBtn);
        topRow.setPadding(new Insets(4, 12, 4, 0));
        topRow.setAlignment(Pos.CENTER_LEFT);

        TableView<TradeRow> table = new TableView<>(tradeRows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No trades yet"));

        TableColumn<TradeRow, String> sideCol = new TableColumn<>("Side");
        sideCol.setCellValueFactory(new PropertyValueFactory<>("side"));
        sideCol.setCellFactory(colourSideCell());
        sideCol.setPrefWidth(55);

        table.getColumns().addAll(
                strCol("Order ID", "orderId",  85),
                sideCol,
                strCol("Ticker",   "ticker",   80),
                strCol("Qty",      "qty",       50),
                strCol("Price ₹",  "price",     85),
                strCol("Total ₹",  "total",    105),
                strCol("Status",   "status",    85),
                strCol("Time",     "time",     150)
        );

        VBox panel = new VBox(0);
        panel.setStyle("-fx-border-color: #30363d; -fx-border-width: 1 0 0 0;");
        panel.setPrefHeight(200);
        VBox.setVgrow(table, Priority.ALWAYS);
        panel.getChildren().addAll(topRow, table);
        return panel;
    }

    // ── Status Bar ─────────────────────────────────────────────────────────
    private HBox buildStatusBar() {
        HBox bar = new HBox(16);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        statusDot = new Region();
        statusDot.getStyleClass().add("status-dot-connected");

        statusConnectionLabel = new Label("Engine running");
        statusUpdateLabel     = new Label("Last update: —");
        statusTradesLabel     = new Label("Trades today: 0");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label version = new Label("TradeDesk v1.0");
        version.getStyleClass().add("muted");

        bar.getChildren().addAll(statusDot, statusConnectionLabel, statusUpdateLabel,
                spacer, statusTradesLabel, version);
        return bar;
    }

    private void updateStatusBar() {
        Platform.runLater(() -> {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            statusUpdateLabel.setText("Last update: " + time);
            statusTradesLabel.setText("Trades today: " + tradesToday);
        });
    }

    // ── TradeObserver callbacks ─────────────────────────────────────────────
    @Override
    public void onOrderFilled(Order order, double executedPrice) {
        Platform.runLater(() -> {
            if (order.getUsername().contains(username)) {
                tradeRows.add(0, new TradeRow(order, executedPrice));
                tradesToday++;
                refreshPortfolio();
                updateHeaderNumbers();
                updateStatusBar();
            }
        });
    }

    @Override
    public void onPriceUpdate(String ticker, double newPrice) {
        Platform.runLater(() -> {
            priceHistory.computeIfAbsent(ticker, k -> new ArrayList<>()).add(newPrice);
            List<Double> hist = priceHistory.get(ticker);
            if (hist.size() > SPARKLINE_POINTS) hist.remove(0);

            tickerRows.stream()
                    .filter(r -> r.getTicker().equals(ticker))
                    .findFirst()
                    .ifPresent(r -> r.updatePrice(newPrice,
                            engine.getStocks().get(ticker).getChangePercent()));
            refreshPortfolio();
            updateHeaderNumbers();
            updateStatusBar();
        });
    }

    @Override
    public void onOrderRejected(Order order, String reason) {
        if (order.getUsername().contains(username))
            setFeedback("❌ Rejected: " + reason, false);
    }

    // ── Order placement ─────────────────────────────────────────────────────
    private void placeOrder(TextField tickerF, TextField qtyF, TextField limitF, boolean isBuy) {
        String ticker = tickerF.getText().trim().toUpperCase();
        String qtyStr = qtyF.getText().trim();
        String limStr = limitF.getText().trim();
        try {
            int qty = Integer.parseInt(qtyStr);
            if (qty <= 0) throw new NumberFormatException();

            if (isBuy) {
                if (limStr.isEmpty()) engine.submitMarketBuy(username, ticker, qty);
                else engine.submitLimitBuy(username, ticker, qty, Double.parseDouble(limStr));
                setFeedback("✅ Buy order placed: " + qty + " × " + ticker, true);
            } else {
                Portfolio pf = engine.getOrCreatePortfolio(username);
                if (pf.getHolding(ticker) < qty) { setFeedback("❌ Not enough shares.", false); return; }
                MarketOrder sell = new MarketOrder("SELL:" + username, ticker, qty);
                sell.setExecutionPrice(engine.getStock(ticker).getCurrentPrice());
                engine.getOrderBooks().get(ticker).addSellOrder(sell);
                setFeedback("✅ Sell order placed: " + qty + " × " + ticker, true);
            }
            refreshPortfolio();
        } catch (NumberFormatException ex) {
            setFeedback("❌ Invalid quantity.", false);
        } catch (InsufficientFundsException ex) {
            setFeedback("❌ " + ex.getMessage(), false);
        } catch (InvalidTickerException ex) {
            setFeedback("❌ Unknown ticker: " + ex.getTicker(), false);
        }
    }

    private void setFeedback(String msg, boolean ok) {
        Platform.runLater(() -> {
            orderFeedback.setTextFill(ok ? Color.web("#3fb950") : Color.web("#f85149"));
            orderFeedback.setText(msg);
        });
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private void populateTickerRows() {
        engine.getStocks().values().forEach(s ->
                tickerRows.add(new TickerRow(s.getTicker(), s.getCompanyName(),
                        s.getSector().getDisplayName(), s.getCurrentPrice(), s.getChangePercent())));
    }

    private void refreshPortfolio() {
        Portfolio pf = engine.getOrCreatePortfolio(username);
        portfolioRows.clear();
        pf.getHoldings().forEach((ticker, qty) -> {
            Stock s = engine.getStock(ticker);
            if (s == null) return;
            double avg = pf.getAvgPrice(ticker);
            double cur = s.getCurrentPrice();
            portfolioRows.add(new PortfolioRow(ticker, qty, avg, cur, (cur - avg) * qty));
        });
    }

    private void updateHeaderNumbers() {
        Portfolio pf = engine.getOrCreatePortfolio(username);
        double cash = pf.getCashBalance();
        double pnl  = engine.getPnL(username);
        cashLabel.setText(String.format("Cash: ₹%,.2f", cash));
        pnlLabel.setText(String.format("P&L: %s₹%,.2f", pnl >= 0 ? "+" : "", pnl));
        pnlLabel.setTextFill(pnl >= 0 ? Color.web("#3fb950") : Color.web("#f85149"));
    }

    private void showLeaderboard() {
        var entries = engine.getLeaderboard();
        VBox list = new VBox(4);
        list.setPadding(new Insets(8));

        int rank = 1;
        for (var e : entries) {
            String medal;
            String styleClass;
            if (rank == 1)      { medal = "🥇"; styleClass = "medal-gold"; }
            else if (rank == 2) { medal = "🥈"; styleClass = "medal-silver"; }
            else if (rank == 3) { medal = "🥉"; styleClass = "medal-bronze"; }
            else                { medal = String.format("%2d.", rank); styleClass = "muted"; }

            Label rankLbl = new Label(medal);
            rankLbl.setPrefWidth(40);
            rankLbl.setFont(Font.font(null, FontWeight.BOLD, 14));
            if (rank <= 3) rankLbl.getStyleClass().add(styleClass);

            Label nameLbl = new Label(e.getKey());
            nameLbl.setPrefWidth(160);
            nameLbl.setTextFill(Color.web("#c9d1d9"));
            nameLbl.setFont(Font.font(null, FontWeight.NORMAL, 13));

            Label valLbl = new Label(String.format("₹%,.2f", e.getValue()));
            valLbl.setTextFill(Color.web("#58a6ff"));
            valLbl.setFont(Font.font("Cascadia Code", FontWeight.BOLD, 13));

            HBox row = new HBox(8, rankLbl, nameLbl, valLbl);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));
            if (rank <= 3) {
                row.setStyle("-fx-background-color: derive(#161b22, 10%); -fx-background-radius: 6;");
            }
            list.getChildren().add(row);
            rank++;
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(Math.min(entries.size() * 40 + 20, 320));
        scroll.setStyle("-fx-background: #0d1117; -fx-background-color: #0d1117;");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("🏆 Leaderboard");
        alert.setHeaderText("Top Traders by Portfolio Value");
        alert.getDialogPane().setContent(scroll);
        alert.getDialogPane().setPrefWidth(420);
        alert.getDialogPane().getScene().getStylesheets().add(
                getClass().getResource("/com/tradedesk/ui/dark-theme.css").toExternalForm());
        alert.showAndWait();
    }

    private void exportCSV() {
        StringBuilder csv = new StringBuilder("OrderID,Side,Ticker,Qty,Price,Total,Status,Time\n");
        tradeRows.forEach(r -> csv.append(r.toCsvLine()).append("\n"));
        java.io.File file = new java.io.File(System.getProperty("user.home"),
                "tradedesk_history_" + username + ".csv");
        try (java.io.FileWriter fw = new java.io.FileWriter(file)) {
            fw.write(csv.toString());
            setFeedback("✅ Exported to " + file.getAbsolutePath(), true);
        } catch (java.io.IOException ex) {
            setFeedback("❌ Export failed: " + ex.getMessage(), false);
        }
    }

    // ── Style factory helpers ────────────────────────────────────────────────
    private <T> TableColumn<T, String> strCol(String title, String prop, double w) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setPrefWidth(w);
        return c;
    }

    private Label sectionHeading(String text) {
        Label l = new Label(text);
        l.setFont(Font.font(null, FontWeight.BOLD, 12));
        l.getStyleClass().add("muted");
        l.setPadding(new Insets(10, 16, 6, 16));
        return l;
    }

    private Label formLbl(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        return l;
    }

    // Change cell with green/red left border
    private Callback<TableColumn<TickerRow, String>, TableCell<TickerRow, String>> colourChangeCellWithBorder() {
        return col -> new TableCell<>() {
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setText(null); setStyle(""); return; }
                setText(val);
                boolean up = !val.startsWith("-");
                setTextFill(up ? Color.web("#3fb950") : Color.web("#f85149"));
                setFont(Font.font(null, FontWeight.BOLD, 12));
                String borderColor = up ? "#3fb950" : "#f85149";
                setStyle("-fx-border-color: transparent transparent transparent " + borderColor + ";" +
                         "-fx-border-width: 0 0 0 3;");
            }
        };
    }

    // Sparkline cell — mini canvas showing recent price trend
    private Callback<TableColumn<TickerRow, String>, TableCell<TickerRow, String>> sparklineCell() {
        return col -> new TableCell<>() {
            private final Canvas canvas = new Canvas(60, 24);

            @Override protected void updateItem(String ticker, boolean empty) {
                super.updateItem(ticker, empty);
                if (empty || ticker == null) { setGraphic(null); return; }

                List<Double> hist = priceHistory.get(ticker);
                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.clearRect(0, 0, 60, 24);

                if (hist == null || hist.size() < 2) {
                    gc.setFill(Color.web("#30363d"));
                    gc.fillRect(10, 10, 40, 4);
                    setGraphic(canvas);
                    return;
                }

                double min = hist.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                double max = hist.stream().mapToDouble(Double::doubleValue).max().orElse(1);
                double range = max - min;
                if (range < 0.01) range = 1;

                boolean up = hist.get(hist.size() - 1) >= hist.get(0);
                gc.setStroke(up ? Color.web("#3fb950") : Color.web("#f85149"));
                gc.setLineWidth(1.5);

                double xStep = 58.0 / (hist.size() - 1);
                gc.beginPath();
                for (int i = 0; i < hist.size(); i++) {
                    double x = 1 + i * xStep;
                    double y = 22 - ((hist.get(i) - min) / range) * 20;
                    if (i == 0) gc.moveTo(x, y);
                    else gc.lineTo(x, y);
                }
                gc.stroke();
                setGraphic(canvas);
            }
        };
    }

    // Colour P&L green / red
    private Callback<TableColumn<PortfolioRow, String>, TableCell<PortfolioRow, String>> colourPnlCell() {
        return col -> new TableCell<>() {
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setText(null); setStyle(""); return; }
                setText(val);
                boolean up = !val.startsWith("-");
                setTextFill(up ? Color.web("#3fb950") : Color.web("#f85149"));
                setFont(Font.font(null, FontWeight.BOLD, 12));
            }
        };
    }

    // Colour side BUY green / SELL red
    private Callback<TableColumn<TradeRow, String>, TableCell<TradeRow, String>> colourSideCell() {
        return col -> new TableCell<>() {
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setText(null); setStyle(""); return; }
                setText(val);
                setTextFill("BUY".equals(val) ? Color.web("#3fb950") : Color.web("#f85149"));
                setFont(Font.font(null, FontWeight.BOLD, 12));
            }
        };
    }
}
