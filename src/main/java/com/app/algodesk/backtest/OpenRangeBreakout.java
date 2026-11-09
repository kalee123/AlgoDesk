package com.app.algodesk.backtest;

import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.TradeDetails;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

// My own customised ORB, kept next to the textbook OpeningRangeBreakout ("orb") so both can be compared.
// Range = 09:15 candle of the selected interval. Entry when the wick breaks the range. 1:1 stop/target.
@Component("orb2")
public class OpenRangeBreakout implements TradingStrategy {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final double STOP_POINTS = 25;   // stop and target distance in index points (1:1)

    private static final LocalTime SQUARE_OFF = LocalTime.of(15, 15);   // close an open trade at this candle's open

    private enum Phase { BUILD_RANGE, WAIT_BREAKOUT, IN_TRADE, DONE }

    @Override
    public String name() {
        return "ORB (mine)";
    }

    @Override
    public String description() {
        return "Work in progress";
    }

    @Override
    public List<TradeDetails> run(List<CandleInfo> candles) {

        List<TradeDetails> tradeList = new ArrayList<>();
        LinkedHashMap<String, List<CandleInfo>> candlesByDate = getCandlesByDate(candles);
        
        for (List<CandleInfo> dayCandles : candlesByDate.values()) {
            TradeDetails tradeDetails = new TradeDetails();
            Phase phase = Phase.BUILD_RANGE;    // stays BUILD_RANGE if the day has no 09:15 candle -> no entry
            for(CandleInfo candle:dayCandles){
                if(isMarketOpenCandle(candle.getTime())){
                    tradeDetails.setRangeHigh(candle.getHigh());
                    tradeDetails.setRangeLow(candle.getLow());
                    phase = Phase.WAIT_BREAKOUT;
                }
                else if(phase == Phase.WAIT_BREAKOUT){
                    if(!candleTime(candle).isBefore(SQUARE_OFF)){
                        phase = Phase.DONE;     // too late for a new entry
                    }
                    else if(breaksBothSides(candle, tradeDetails)){
                        phase = Phase.DONE;     // can't tell which side broke first: skip the day
                    }
                    else if(checkValidEntry(candle, tradeDetails)){
                        // Stop order at the range level; if the candle opens beyond it, the fill is the open
                        double entry;
                        if(tradeDetails.getDirection() == TradeDetails.Direction.LONG){
                            entry = Math.max(candle.getOpen(), tradeDetails.getRangeHigh());
                            tradeDetails.setTarget(entry + STOP_POINTS);
                            tradeDetails.setStopLoss(entry - STOP_POINTS);
                        }
                        else {
                            entry = Math.min(candle.getOpen(), tradeDetails.getRangeLow());
                            tradeDetails.setTarget(entry - STOP_POINTS);
                            tradeDetails.setStopLoss(entry + STOP_POINTS);
                        }
                        tradeDetails.setEntryPrice(entry);
                        tradeDetails.setEntryTime(candle.getTime());
                        tradeDetails.setDate(Instant.ofEpochSecond(candle.getTime()).atZone(IST).toLocalDate());
                        phase = Phase.IN_TRADE;
                    }
                }
                if(phase == Phase.IN_TRADE){
                    boolean isLong = tradeDetails.getDirection() == TradeDetails.Direction.LONG;
                    double stop = tradeDetails.getStopLoss();
                    double target = tradeDetails.getTarget();
                    boolean stopHit = isLong ? candle.getLow() <= stop : candle.getHigh() >= stop;
                    boolean targetHit = isLong ? candle.getHigh() >= target : candle.getLow() <= target;

                    if(!candleTime(candle).isBefore(SQUARE_OFF)){
                        tradeList.add(exit(tradeDetails, candle, candle.getOpen(), "SQUARE_OFF"));
                        phase = Phase.DONE;
                    }
                    else if(stopHit){
                        // Checked before the target: if one candle hits both, assume the stop came first.
                        // A gap past the stop fills at the open (worse than the stop).
                        double price = isLong ? Math.min(stop, candle.getOpen()) : Math.max(stop, candle.getOpen());
                        tradeList.add(exit(tradeDetails, candle, price, "STOP_LOSS"));
                        phase = Phase.DONE;
                    }
                    else if(targetHit){
                        double price = isLong ? Math.max(target, candle.getOpen()) : Math.min(target, candle.getOpen());
                        tradeList.add(exit(tradeDetails, candle, price, "TARGET"));
                        phase = Phase.DONE;
                    }
                }

            }
            // Data ended (e.g. no 15:15 candle) with the trade still open: close at the last close
            if(phase == Phase.IN_TRADE){
                CandleInfo last = dayCandles.get(dayCandles.size() - 1);
                tradeList.add(exit(tradeDetails, last, last.getClose(), "DAY_END"));
            }
        }

        return tradeList;
    }

    // Fills the exit fields; the same object is the finished trade the UI shows
    private TradeDetails exit(TradeDetails d, CandleInfo candle, double price, String reason){
        d.close(candle.getTime(), price, reason);
        return d;
    }

    private LocalTime candleTime(CandleInfo candle){
        return Instant.ofEpochSecond(candle.getTime()).atZone(IST).toLocalTime();
    }

    // Groups candles by IST calendar date, e.g. "2024-01-15". Insertion order = feed order, so days stay chronological.
    private LinkedHashMap<String, List<CandleInfo>> getCandlesByDate(List<CandleInfo> candles) {
        LinkedHashMap<String, List<CandleInfo>> candlesByDate = new LinkedHashMap<>();
        for (CandleInfo candle : candles) {
            String date = Instant.ofEpochSecond(candle.getTime()).atZone(IST).toLocalDate().toString();
            candlesByDate.computeIfAbsent(date, d -> new ArrayList<>()).add(candle);
        }
        return candlesByDate;
    }

    private boolean isMarketOpenCandle(long timestamp) {
        LocalDateTime candleTime = Instant.ofEpochSecond(timestamp)
                                        .atZone(IST)
                                        .toLocalDateTime();
        return candleTime.toLocalTime().equals(LocalTime.of(9, 15));
    }

    // Touch rule, same for both sides: the wick crossing the level is the breakout
    private boolean checkValidEntry(CandleInfo candleInfo, TradeDetails tradeDetails){
        double rangeHigh = tradeDetails.getRangeHigh();
        double rangeLow = tradeDetails.getRangeLow();
        if(candleInfo.getHigh() > rangeHigh){
            tradeDetails.setDirection(TradeDetails.Direction.LONG);
            return true;
        }
        else if(candleInfo.getLow() < rangeLow){
            tradeDetails.setDirection(TradeDetails.Direction.SHORT);
            return true;
        }
        return false;
    }

    private boolean breaksBothSides(CandleInfo candleInfo, TradeDetails tradeDetails){
        return candleInfo.getHigh() > tradeDetails.getRangeHigh() && candleInfo.getLow() < tradeDetails.getRangeLow();
    }
}
