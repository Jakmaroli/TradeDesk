# TradeDesk — Real-Time Stock Trading Simulator

> Java OOP Project | 1BCS302 | NIE

## Overview
A real-time, multi-user stock trading simulator built as a client–server application.

## Features
- Live price simulation (Random Walk / Brownian Motion)
- Market and Limit orders with priority order book
- Multi-user support via TCP sockets
- JavaFX dashboard: live ticker strip, order form, portfolio, trade history
- Leaderboard with P&L computed via Java Streams
- CSV trade history export
- MySQL persistence via JDBC connection pool
- JUnit 5 test suite

## OOP Concepts Covered
| Concept | Where |
|---|---|
| Classes & Objects | Order, Stock, Portfolio, Trade |
| Abstract Classes | Order (abstract) |
| Inheritance | MarketOrder, LimitOrder extends Order |
| Interfaces | Tradeable, Persistable |
| Enums | OrderType, OrderStatus, StockSector |
| Custom Exceptions | InsufficientFundsException, InvalidTickerException, OrderNotFoundException |
| Collections & Generics | PriorityQueue, ConcurrentHashMap, BlockingQueue |
| Streams & Lambdas | Leaderboard, P&L calculation |
| Multithreading | MatchingEngine thread, PriceFeed thread, ClientHandler threads |
| Sockets | TradeClient ↔ TradeServer |
| JDBC | ConnectionPool, DAOs |
| File I/O | CSV export |
| Design Patterns | Observer, Strategy, MVC |
| JUnit 5 | MatchingEngineTest |

## Work Split
| Member | Responsibility |
|---|---|
| **Member 1** | Trading Engine, JavaFX UI, JUnit tests ,Maven build ,Git setup, Integration|
| **Member 2** | Data Models, DB schema, DAOs |
| **Member 3** | TCP Server, ClientHandler, PriceFeedThread |
| **Member 4** | Demo script, Final report |

## Running

> **First-time setup:** `DBConfig.java` holds DB credentials and is git-ignored,
> so a fresh clone will not compile until you create it:
> ```bash
> cp src/main/java/com/tradedesk/db/DBConfig.java.example src/main/java/com/tradedesk/db/DBConfig.java
> ```
> Then set your MySQL password (edit the file, or set `TRADEDESK_DB_PASSWORD`) and
> load the schema once: `mysql -u root -p < src/main/resources/db/schema.sql`.

```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Start the server (matching engine + market maker + MySQL persistence)
mvn exec:java -Dexec.mainClass=com.tradedesk.network.TradeServer

# Launch UI (requires JavaFX)
mvn javafx:run
```

## Tech Stack
- Java 17+
- JavaFX 21
- MySQL 8 + JDBC
- Maven
- JUnit 5
