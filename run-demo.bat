@echo off
REM ============================================================
REM  TradeDesk demo launcher (Windows)
REM  Starts the TradeServer, waits, then opens the JavaFX client.
REM  Run from the TradeDesk project folder:  run-demo.bat
REM ============================================================
setlocal

echo ============================================
echo   TradeDesk - demo launcher
echo ============================================

REM 1. First-time: create DBConfig.java from the template if missing (git-ignored)
if not exist "src\main\java\com\tradedesk\db\DBConfig.java" (
    echo [setup] Creating DBConfig.java from template...
    copy "src\main\java\com\tradedesk\db\DBConfig.java.example" "src\main\java\com\tradedesk\db\DBConfig.java" >nul
    echo [setup] Edit DBConfig.java with your MySQL password for DB features.
)

REM 2. (Optional) load the schema once - needs mysql on PATH + your root password
REM     mysql -u root -p ^< src\main\resources\db\schema.sql

REM 3. Compile
echo [build] Compiling...
call mvn -q clean compile
if errorlevel 1 ( echo [build] FAILED & exit /b 1 )

REM 4. Start the server in its own window
echo [server] Launching TradeServer (new window)...
start "TradeDesk Server" cmd /k mvn -q exec:java -Dexec.mainClass=com.tradedesk.network.TradeServer

REM 5. Give the server a few seconds to bind the port, then open the UI
echo [client] Waiting for server, then launching the dashboard...
timeout /t 6 /nobreak >nul
call mvn -q javafx:run

echo [done] Close the server window to stop everything.
endlocal
