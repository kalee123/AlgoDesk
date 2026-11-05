package com.app.algodesk.broker.order;

// At what price the order executes
public enum OrderType {

    // Execute now at the best available price
    MARKET("MARKET", "Market order"),
    // Execute only at this price or better (buy at or below, sell at or above)
    LIMIT("LIMIT", "Limit order"),
    // Stop-loss: waits until the price hits trigger_price, then becomes a LIMIT order at price
    SL("SL", "Stop-loss limit order"),
    // Stop-loss market: waits until the price hits trigger_price, then becomes a MARKET order
    SL_M("SL-M", "Stop-loss market order");

    private final String kiteValue;
    private final String description;

    OrderType(String kiteValue, String description) {
        this.kiteValue = kiteValue;
        this.description = description;
    }

    public String getKiteValue() {
        return kiteValue;
    }

    public String getDescription() {
        return description;
    }

    // SL and SL-M need a trigger_price
    public boolean needsTriggerPrice() {
        return this == SL || this == SL_M;
    }

    // LIMIT and SL need a price; MARKET and SL-M don't
    public boolean needsPrice() {
        return this == LIMIT || this == SL;
    }

    public static OrderType fromKite(String value) {
        for (OrderType t : values()) {
            if (t.kiteValue.equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown order type: " + value);
    }
}
