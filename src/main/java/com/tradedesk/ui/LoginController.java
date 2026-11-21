package com.tradedesk.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;

import com.tradedesk.db.UserDAO;

/**
 * Login screen — MVC Controller for the login view.
 * Covers MVC pattern; JavaFX controls.
 * Owner: Member 1 (Jak)
 */
public class LoginController {

    private final Stage stage;

    public LoginController(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        stage.setTitle("TradeDesk — Login");

        // ── Root ──────────────────────────────────────────────────────────
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(60));

        // ── Logo / Title ──────────────────────────────────────────────────
        Text logo = new Text("📈 TradeDesk");
        logo.setFont(Font.font("Monospace", FontWeight.BOLD, 38));
        logo.setFill(Color.web("#00d4aa"));

        Text sub = new Text("Real-Time Stock Trading Simulator");
        sub.setFont(Font.font("Segoe UI", 14));
        sub.getStyleClass().add("muted");

        // ── Card container ────────────────────────────────────────────────
        VBox card = new VBox(16);
        card.setPadding(new Insets(28, 36, 28, 36));
        card.setStyle("-fx-background-color: #161b22; -fx-border-color: #30363d; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        card.setMaxWidth(380);

        // ── Form ──────────────────────────────────────────────────────────
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(14);

        Label userLabel = new Label("Username");
        userLabel.getStyleClass().add("muted");
        TextField userField = new TextField();
        userField.setPromptText("e.g. trader_01");
        userField.setMaxWidth(Double.MAX_VALUE);

        Label passLabel = new Label("Password");
        passLabel.getStyleClass().add("muted");
        PasswordField passField = new PasswordField();
        passField.setPromptText("••••••••");
        passField.setMaxWidth(Double.MAX_VALUE);

        GridPane.setFillWidth(userField, true);
        GridPane.setFillWidth(passField, true);
        form.add(userLabel, 0, 0); form.add(userField, 1, 0);
        form.add(passLabel, 0, 1); form.add(passField, 1, 1);
        form.setMaxWidth(Double.MAX_VALUE);

        // ── Error label ───────────────────────────────────────────────────
        Label errorLabel = new Label();
        errorLabel.setTextFill(Color.web("#f85149"));
        errorLabel.setVisible(false);
        errorLabel.setWrapText(true);

        // ── Buttons ───────────────────────────────────────────────────────
        UserDAO users = new UserDAO();

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().add("accent-btn");
        loginBtn.setStyle("-fx-font-size: 14px; -fx-padding: 10 0 10 0;");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(loginBtn, Priority.ALWAYS);

        Button registerBtn = new Button("Register");
        registerBtn.setStyle("-fx-font-size: 14px; -fx-padding: 10 0 10 0;");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(registerBtn, Priority.ALWAYS);

        HBox authRow = new HBox(12, loginBtn, registerBtn);
        authRow.setMaxWidth(Double.MAX_VALUE);

        Button demoBtn = new Button("Quick Demo (no server)");
        demoBtn.setMaxWidth(Double.MAX_VALUE);
        demoBtn.setStyle("-fx-padding: 8 0 8 0;");

        // ── Action: Login (authenticate against the DB) ────────────────────
        loginBtn.setOnAction(e -> {
            String username = userField.getText().trim();
            String password = passField.getText();
            if (username.isEmpty() || password.isEmpty()) {
                showError(errorLabel, "Please enter username and password.");
                return;
            }
            try {
                if (users.authenticate(username, password)) {
                    openDashboard(username, true);          // DB-backed session
                } else {
                    showError(errorLabel, "Invalid username or password.");
                }
            } catch (RuntimeException ex) {
                showError(errorLabel, "Database unavailable — try Quick Demo.");
            }
        });

        // ── Action: Register (create the user in the DB) ───────────────────
        registerBtn.setOnAction(e -> {
            String username = userField.getText().trim();
            String password = passField.getText();
            if (username.isEmpty() || password.isEmpty()) {
                showError(errorLabel, "Please enter username and password.");
                return;
            }
            try {
                if (users.register(username, password)) {
                    showInfo(errorLabel, "Registered! You can log in now.");
                } else {
                    showError(errorLabel, "Username taken, or password too short (min 4).");
                }
            } catch (RuntimeException ex) {
                showError(errorLabel, "Database unavailable — try Quick Demo.");
            }
        });

        // Enter key on password → login
        passField.setOnAction(e -> loginBtn.fire());

        // Offline fallback: no DB, no server, default starting balance
        demoBtn.setOnAction(e -> openDashboard("DemoTrader", false));

        card.getChildren().addAll(form, errorLabel, authRow, demoBtn);

        root.getChildren().addAll(logo, sub, card);

        Scene scene = new Scene(root, 480, 440);
        scene.getStylesheets().add(
                getClass().getResource("/com/tradedesk/ui/dark-theme.css").toExternalForm());
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private void openDashboard(String username, boolean dbBacked) {
        DashboardController dashboard = new DashboardController(stage, username, dbBacked);
        dashboard.show();
    }

    private void showError(Label label, String msg) {
        label.setTextFill(Color.web("#f85149"));
        label.setText(msg);
        label.setVisible(true);
    }

    private void showInfo(Label label, String msg) {
        label.setTextFill(Color.web("#3fb950"));
        label.setText(msg);
        label.setVisible(true);
    }
}
