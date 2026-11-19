package edu.campus.circulation.domain;

/** Reservations and completed quantities are derived from exchange records, avoiding duplicate counters. */
public record Inventory(int quantity, int reserved, int completed) {
    public int available() { return quantity - reserved - completed; }
}
