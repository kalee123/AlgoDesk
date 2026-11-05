package com.app.algodesk.broker.order;

// How long you intend to hold, which decides margin and whether the position is auto-closed
public enum ProductType {

    // Equity delivery: full payment, shares go to your demat and become holdings (T+1)
    CNC("CNC", "Cash & Carry (equity delivery)"),
    // Futures & options carried overnight, up to expiry, with normal (full) margin
    NRML("NRML", "Normal (F&O overnight)"),
    // Intraday for equity AND F&O: lower margin, auto squared off by the broker before close (~3:20 PM)
    MIS("MIS", "Margin Intraday Squareoff"),
    // Buy delivery shares with partial payment; the broker funds the rest and charges interest
    MTF("MTF", "Margin Trading Facility");

    private final String kiteValue;
    private final String description;

    ProductType(String kiteValue, String description) {
        this.kiteValue = kiteValue;
        this.description = description;
    }

    public String getKiteValue() {
        return kiteValue;
    }

    public String getDescription() {
        return description;
    }

    public boolean isIntraday() {
        return this == MIS;
    }

    public static ProductType fromKite(String value) {
        for (ProductType p : values()) {
            if (p.kiteValue.equalsIgnoreCase(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown product: " + value);
    }
}
