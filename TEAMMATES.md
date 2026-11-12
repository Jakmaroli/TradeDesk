# TradeDesk — Team Guide

## Getting Started

### Prerequisites
- Java 21+
- Maven 3.9+
- JavaFX SDK 21 (pulled automatically by Maven)
- MySQL 8.0+ (for Member 2's DB work)

### Clone & Run

```bash
git clone <repo-url>
cd TradeDesk

# Compile
mvn compile

# Run tests (17 unit tests in MatchingEngineTest)
mvn test

# Launch the JavaFX dashboard
mvn javafx:run
```

The app opens a login screen — click **Quick Demo (no server)** to explore
the trading UI without needing Member 3's server running.

---

## Who Does What

### Member 1 (Jak) — UI + Engine
Already implemented:
- `MatchingEngine`, `OrderBook`, `RandomWalkStrategy`, all model classes
- JavaFX dashboard (`DashboardController`, `LoginController`, dark theme CSS)
- `TradeClient` socket client with the protocol parser

### Member 2 — Database Layer
Package: `com.tradedesk.db`

**What to build:**
1. **MySQL schema** — create a `tradedesk` database with tables:
   - `users` (username, hashed password, created_at)
   - `portfolios` (user_id, cash_balance)
   - `holdings` (user_id, ticker, quantity, avg_buy_price)
   - `trade_history` (order_id, user, side, ticker, qty, price, timestamp)
2. **DAO classes** — create data-access objects in `com.tradedesk.db`:
   - `UserDAO` — register, authenticate (hash passwords with BCrypt)
   - `PortfolioDAO` — load/save portfolio state
   - `TradeHistoryDAO` — insert trades, query history
3. **Wire real credentials** — `DBConfig.java` reads from environment
   variables (`TRADEDESK_DB_URL`, `TRADEDESK_DB_USER`, `TRADEDESK_DB_PASSWORD`).
   Set these in your shell or IDE run config. The file is in `.gitignore` so
   local fallback values stay off GitHub.
4. **Use `ConnectionPool`** — it's already built; call `ConnectionPool.getInstance().borrow()`
   and always return connections in a `finally` block.

### Member 3 — Network / Server
Package: `com.tradedesk.network`

**What to build in `TradeServer.java`:**
1. **Accept loop** — `ServerSocket` on port 9876, spawn a `ClientHandler` thread per connection
2. **`ClientHandler` (new class, implements `Runnable`)** — read lines from the client socket, parse the protocol (see below), forward orders to `MatchingEngine`, send back `PRICE`/`FILL`/`REJECT` responses
3. **`PriceFeedThread` (new class)** — subscribe to engine price updates via `TradeObserver`, broadcast `PRICE|TICKER|value` to all connected clients
4. **Disconnect handling** — clean up socket, remove from client list, log disconnections gracefully
5. **Thread safety** — use `CopyOnWriteArrayList` for the client list; synchronise writes to individual client sockets

`TradeClient.java` is already implemented and parses incoming messages — test your server against it.

### Member 4 — Integration & Polish

**What to build:**
1. **Integration glue** — wire `LoginController` to `UserDAO` for real authentication; connect `DashboardController` to `TradeClient` so it can trade over the network instead of only locally
2. **Demo launch script** — a `run-demo.bat` / `run-demo.sh` that starts MySQL, runs the schema migration, launches `TradeServer` in the background, launches the JavaFX client
3. **Final README.md** — project overview, screenshots, architecture diagram, how to build/run, team credits

---

## Message Protocol

Plain-text, newline-delimited, fields separated by `|`.

### Client to Server

| Command | Format | Example |
|---------|--------|---------|
| Market Buy | `BUY\|TICKER\|QTY` | `BUY\|RELIANCE\|10` |
| Limit Buy | `BUY\|TICKER\|QTY\|LIMIT_PRICE` | `BUY\|TCS\|5\|4100.00` |
| Market Sell | `SELL\|TICKER\|QTY` | `SELL\|INFOSYS\|20` |
| Limit Sell | `SELL\|TICKER\|QTY\|LIMIT_PRICE` | `SELL\|TCS\|5\|4100.00` |
| Cancel Order | `CANCEL\|ORDER_ID` | `CANCEL\|ORD-0042` |
| Hello | `HELLO\|USERNAME` | `HELLO\|trader_01` |

### Server to Client

| Event | Format | Example |
|-------|--------|---------|
| Price Update | `PRICE\|TICKER\|PRICE` | `PRICE\|RELIANCE\|2863.50` |
| Order Filled | `FILL\|ORDER_ID\|TICKER\|QTY\|PRICE` | `FILL\|ORD-0042\|RELIANCE\|10\|2860.00` |
| Order Rejected | `REJECT\|ORDER_ID\|REASON` | `REJECT\|ORD-0043\|Insufficient funds` |
| Leaderboard | `LEADERBOARD\|user1:score1,user2:score2,...` | `LEADERBOARD\|jak:1050000,demo:990000` |

### Rules
- All prices are in INR, decimal with 2 places
- Tickers are uppercase NSE symbols: RELIANCE, TCS, INFOSYS, HDFCBANK, WIPRO, SUNPHARMA, MARUTI, AIRTEL
- HELLO must be the first message after connecting
- Server broadcasts PRICE updates every ~2 seconds

---

## Project Structure

```
TradeDesk/
├── src/main/java/com/tradedesk/
│   ├── model/       — Stock, Order, Portfolio, exceptions
│   ├── engine/      — MatchingEngine, OrderBook, pricing
│   ├── network/     — TradeServer (Member 3), TradeClient
│   ├── db/          — DBConfig, ConnectionPool, DAOs (Member 2)
│   └── ui/          — JavaFX controllers, row models, CSS
├── src/test/java/   — JUnit 5 tests
└── pom.xml
```
