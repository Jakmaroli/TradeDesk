-- TradeDesk schema (MySQL 8+). Run:  mysql -u root -p < src/main/resources/db/schema.sql
CREATE DATABASE IF NOT EXISTS tradedesk CHARACTER SET utf8mb4;
USE tradedesk;

CREATE TABLE IF NOT EXISTS users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,              -- BCrypt hash, never plain text
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS portfolios (
    user_id      INT PRIMARY KEY,
    cash_balance DECIMAL(15,2) NOT NULL DEFAULT 1000000.00,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS holdings (
    user_id       INT         NOT NULL,
    ticker        VARCHAR(20) NOT NULL,
    quantity      INT         NOT NULL,
    avg_buy_price DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (user_id, ticker),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Orders as submitted (no FK on username so engine bots / demo users still log fine)
CREATE TABLE IF NOT EXISTS orders (
    order_id    VARCHAR(20)  PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    side        ENUM('BUY','SELL') NOT NULL,
    ticker      VARCHAR(20)  NOT NULL,
    quantity    INT          NOT NULL,
    order_type  ENUM('MARKET','LIMIT') NOT NULL,
    limit_price DECIMAL(12,2) NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_orders_user (username)
) ENGINE=InnoDB;

-- One row per side of every executed trade (buyer row + seller row)
CREATE TABLE IF NOT EXISTS trade_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    VARCHAR(20)  NOT NULL,
    username    VARCHAR(50)  NOT NULL,
    side        ENUM('BUY','SELL') NOT NULL,
    ticker      VARCHAR(20)  NOT NULL,
    quantity    INT          NOT NULL,
    price       DECIMAL(12,2) NOT NULL,
    executed_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trades_user (username, executed_at)
) ENGINE=InnoDB;
