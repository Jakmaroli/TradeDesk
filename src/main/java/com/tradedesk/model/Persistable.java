package com.tradedesk.model;

/**
 * Interface: anything that can be saved to / loaded from a store.
 * Covers Module 3 — Interfaces.
 * Owner: Member 2
 */
public interface Persistable {
    void save();
    void load(int id);
    void delete();
}
