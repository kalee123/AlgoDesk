package com.app.algodesk.models;


import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Headline numbers for a list of trades. Everything is in points per share (or % of entry),
// before brokerage, taxes and slippage.
public class BacktestSummary {

    private int trades;
    private int wins;
    private int losses;
    private double winRate;          // % of trades with pnl > 0
    private double totalPnl;
    private double totalPnlPercent;  // sum of each trade's %: what you'd make putting the same capital in every trade
    private double avgWin;
    private double avgLoss;          // negative
    private Double profitFactor;     // gross profit / gross loss; null when there are no losing trades
    private double maxDrawdown;      // biggest drop from a peak in cumulative pnl, as a positive number
    private double bestTrade;
    private double worstTrade;
    private final Map<String, Integer> exitReasons = new TreeMap<>();   // TARGET -> 12, STOP_LOSS -> 20, ...

    public BacktestSummary(List<TradeDetails> tradeList) {
        trades = tradeList.size();
        if (trades == 0) {
            return;
        }
        double grossProfit = 0;
        double grossLoss = 0;
        double equity = 0;
        double peak = 0;
        bestTrade = -Double.MAX_VALUE;
        worstTrade = Double.MAX_VALUE;

        for (TradeDetails t : tradeList) {
            double pnl = t.getPnl();
            if (pnl > 0) {
                wins++;
                grossProfit += pnl;
            } else if (pnl < 0) {
                losses++;
                grossLoss += pnl;
            }
            totalPnlPercent += t.getPnlPercent();
            bestTrade = Math.max(bestTrade, pnl);
            worstTrade = Math.min(worstTrade, pnl);
            exitReasons.merge(t.getExitReason(), 1, Integer::sum);

            equity += pnl;
            peak = Math.max(peak, equity);
            maxDrawdown = Math.max(maxDrawdown, peak - equity);
        }
        totalPnl = grossProfit + grossLoss;
        winRate = wins * 100.0 / trades;
        avgWin = wins > 0 ? grossProfit / wins : 0;
        avgLoss = losses > 0 ? grossLoss / losses : 0;
        profitFactor = grossLoss < 0 ? grossProfit / -grossLoss : null;
    }

    public int getTrades() { return trades; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public double getWinRate() { return winRate; }
    public double getTotalPnl() { return totalPnl; }
    public double getTotalPnlPercent() { return totalPnlPercent; }
    public double getAvgWin() { return avgWin; }
    public double getAvgLoss() { return avgLoss; }
    public Double getProfitFactor() { return profitFactor; }
    public double getMaxDrawdown() { return maxDrawdown; }
    public double getBestTrade() { return bestTrade; }
    public double getWorstTrade() { return worstTrade; }
    public Map<String, Integer> getExitReasons() { return exitReasons; }
}
