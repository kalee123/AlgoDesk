package com.app.algodesk.broker.order;

// How the order is placed. Part of the Kite URL: POST /orders/{variety}
public enum OrderVariety {

    REGULAR("regular", "Regular order during market hours"),
    // Placed after market hours (or before open) and sent to the exchange when the market opens
    AMO("amo", "After Market Order"),
    // Intraday order with a compulsory stop-loss attached; lower margin because the risk is capped
    CO("co", "Cover Order"),
    // A big order split into smaller "legs" sent one after another, so the full size isn't visible to the market
    ICEBERG("iceberg", "Iceberg Order"),
    // Bid in the exchange's auction session (e.g. to buy back shares you failed to deliver)
    AUCTION("auction", "Auction Order");

    private final String kiteValue;
    private final String description;

    OrderVariety(String kiteValue, String description) {
        this.kiteValue = kiteValue;
        this.description = description;
    }

    public String getKiteValue() {
        return kiteValue;
    }

    public String getDescription() {
        return description;
    }

    public static OrderVariety fromKite(String value) {
        for (OrderVariety v : values()) {
            if (v.kiteValue.equalsIgnoreCase(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unknown order variety: " + value);
    }
}
