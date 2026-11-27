#!/usr/bin/env bash
# ============================================================
#  TradeDesk demo launcher (macOS / Linux)
#  Starts the TradeServer in the background, then the JavaFX client.
#  Run from the TradeDesk project folder:  ./run-demo.sh
# ============================================================
set -e

echo "============================================"
echo "  TradeDesk - demo launcher"
echo "============================================"

DB=src/main/java/com/tradedesk/db

# 1. First-time: create DBConfig.java from the template if missing (git-ignored)
if [ ! -f "$DB/DBConfig.java" ]; then
    echo "[setup] Creating DBConfig.java from template..."
    cp "$DB/DBConfig.java.example" "$DB/DBConfig.java"
    echo "[setup] Edit DBConfig.java with your MySQL password for DB features."
fi

# 2. (Optional) load the schema once - needs mysql on PATH + your root password
#     mysql -u root -p < src/main/resources/db/schema.sql

# 3. Compile
echo "[build] Compiling..."
mvn -q clean compile

# 4. Start the server in the background; stop it automatically on exit
echo "[server] Launching TradeServer..."
mvn -q exec:java -Dexec.mainClass=com.tradedesk.network.TradeServer &
SERVER_PID=$!
trap 'echo "[server] stopping..."; kill "$SERVER_PID" 2>/dev/null || true' EXIT

# 5. Give the server a few seconds to bind the port, then open the UI
echo "[client] Waiting for server, then launching the dashboard..."
sleep 6
mvn -q javafx:run

echo "[done] Dashboard closed; stopping server."
