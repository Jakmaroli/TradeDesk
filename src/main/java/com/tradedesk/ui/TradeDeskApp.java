package com.tradedesk.ui;

import com.tradedesk.engine.MatchingEngine;
import com.tradedesk.engine.RandomWalkStrategy;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * JavaFX application entry point.
 * Covers Module 1 — Java basics; JavaFX.
 * Owner: Member 1 (Jak)
 */
public class TradeDeskApp extends Application {

    /** Global engine instance shared between UI and (optionally) the network layer. */
    public static MatchingEngine ENGINE;

    @Override
    public void start(Stage primaryStage) {
        // Start the matching engine in its own thread
        ENGINE = new MatchingEngine(new RandomWalkStrategy());
        Thread engineThread = new Thread(ENGINE, "MatchingEngine");
        engineThread.setDaemon(true);
        engineThread.start();

        // Show login first
        LoginController login = new LoginController(primaryStage);
        login.show();
    }

    @Override
    public void stop() {
        if (ENGINE != null) ENGINE.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
