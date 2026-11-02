package com.app.algodesk.service;

import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.broker.kite.KiteApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class InstrumentDataService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentDataService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final int CSV_COLUMNS = 12;
    private static final int BATCH_SIZE = 1000;

    // PostgreSQL upsert: insert new instruments, update the ones already stored.
    // ON CONFLICT relies on the uk_instruments_exchange_symbol unique constraint.
    private static final String UPSERT_SQL = """
            INSERT INTO instruments (instrument_token, exchange_token, tradingsymbol, name, last_price,
                                     expiry, strike, tick_size, lot_size, instrument_type, segment, exchange)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (exchange, tradingsymbol) DO UPDATE SET
                instrument_token = EXCLUDED.instrument_token,
                exchange_token   = EXCLUDED.exchange_token,
                name             = EXCLUDED.name,
                last_price       = EXCLUDED.last_price,
                expiry           = EXCLUDED.expiry,
                strike           = EXCLUDED.strike,
                tick_size        = EXCLUDED.tick_size,
                lot_size         = EXCLUDED.lot_size,
                instrument_type  = EXCLUDED.instrument_type,
                segment          = EXCLUDED.segment
            """;

    @Autowired
    KiteApiClient kiteApiClient;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // Runtime data lives outside the project sources (see algodesk.data-dir in application.yml)
    @Value("${algodesk.data-dir}")
    String dataDir;

    // In memory, so a restart refreshes once more; harmless
    private LocalDate lastRefreshDate;

    // Called from both the login callback and the morning scheduler; whichever runs first does the work.
    // synchronized: if both fire together, the second waits, then sees today's date and skips.
    public synchronized void refreshIfNeeded(String accessToken) {
        LocalDate today = LocalDate.now(IST);
        if (today.equals(lastRefreshDate)) {
            log.info("Instruments already refreshed today, skipping");
            return;
        }
        try {
            Path file = fetchInstruments(accessToken);
            log.info("Instruments downloaded to {}", file.toAbsolutePath());
            persistInstruments(file);
            lastRefreshDate = today;   // only after success, so a failure is retried next time
        } catch (Exception e) {
            // Keep yesterday's data; the app still works without today's list
            log.warn("Instrument refresh failed: {}", e.getMessage());
        }
    }

    public Path fetchInstruments(String accessToken) throws Exception {
        Path target = Path.of(dataDir, "kite", "instruments.csv");
        return kiteApiClient.downloadTo(UrlConstants.KITE_INSTRUMENTS_PATH, accessToken, target);
    }

    // Upserts every row of the CSV by (exchange, tradingsymbol), then removes expired contracts.
    // Plain JDBC instead of JPA: ~110k rows, and Hibernate can't batch inserts with IDENTITY ids,
    // so saveAll() would send one INSERT per row (and show-sql would print every one).
    private int persistInstruments(Path file) throws IOException {
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int total = 0;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            reader.readLine();   // header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                batch.add(toRow(parseCsvLine(line)));
                if (batch.size() == BATCH_SIZE) {
                    jdbcTemplate.batchUpdate(UPSERT_SQL, batch);
                    total += batch.size();
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(UPSERT_SQL, batch);
            total += batch.size();
        }

        // Expired contracts' tokens can be reused for new ones, so drop them to keep token lookups unique
        int removed = jdbcTemplate.update("DELETE FROM instruments WHERE expiry < ?", LocalDate.now(IST));
        log.info("Instruments saved: {} upserted, {} expired removed", total, removed);
        return total;
    }

    // CSV columns, in order:
    // instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,segment,exchange
    private static Object[] toRow(List<String> f) {
        return new Object[]{
                Long.parseLong(f.get(0)),
                Long.parseLong(f.get(1)),
                f.get(2),
                blankToNull(f.get(3)),
                decimal(f.get(4)),
                f.get(5).isBlank() ? null : LocalDate.parse(f.get(5)),   // blank for stocks and indices
                decimal(f.get(6)),
                decimal(f.get(7)),
                f.get(8).isBlank() ? null : Integer.parseInt(f.get(8)),
                blankToNull(f.get(9)),
                blankToNull(f.get(10)),
                f.get(11)
        };
    }

    // Splits one CSV line, honouring double quotes (Kite quotes the name column, e.g. "BANKEX")
    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>(12);
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');   // "" inside quotes is an escaped quote
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        if (fields.size() != CSV_COLUMNS) {
            throw new IllegalArgumentException("Expected " + CSV_COLUMNS + " columns but got " + fields.size() + ": " + line);
        }
        return fields;
    }

    private static String blankToNull(String s) {
        return s.isBlank() ? null : s;
    }

    private static BigDecimal decimal(String s) {
        return s.isBlank() ? null : new BigDecimal(s);
    }
}
