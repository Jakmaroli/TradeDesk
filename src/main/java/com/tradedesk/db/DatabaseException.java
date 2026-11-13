package com.tradedesk.db;

/** Unchecked wrapper so UI/network code isn't forced to catch SQLException. Owner: Member 2 */
public class DatabaseException extends RuntimeException {
    public DatabaseException(String message, Throwable cause) { super(message, cause); }
}
