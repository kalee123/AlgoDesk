package com.app.algodesk.broker.order;

// How long an unfilled order stays at the exchange
public enum OrderValidity {

    // Until the end of the trading day, then cancelled
    DAY("DAY", "Valid for the day"),
    // Fill whatever is possible immediately, cancel the rest
    IOC("IOC", "Immediate or Cancel"),
    // Valid for validity_ttl minutes (1 to 1440), then cancelled
    TTL("TTL", "Valid for N minutes");

    private final String kiteValue;
    private final String description;

    OrderValidity(String kiteValue, String description) {
        this.kiteValue = kiteValue;
        this.description = description;
    }

    public String getKiteValue() {
        return kiteValue;
    }

    public String getDescription() {
        return description;
    }

    public static OrderValidity fromKite(String value) {
        for (OrderValidity v : values()) {
            if (v.kiteValue.equalsIgnoreCase(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unknown validity: " + value);
    }
}
