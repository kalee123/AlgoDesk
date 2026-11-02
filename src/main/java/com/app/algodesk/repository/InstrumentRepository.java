package com.app.algodesk.repository;

import com.app.algodesk.entity.InstrumentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

    // e.g. ("NSE", "RELIANCE") -> instrument token for the WebSocket
    Optional<InstrumentEntity> findByExchangeAndTradingsymbol(String exchange, String tradingsymbol);

    // Map an incoming tick back to its instrument. Only safe while expired rows are removed on refresh,
    // because a token can belong to a different contract after expiry.
    Optional<InstrumentEntity> findByInstrumentToken(Long instrumentToken);

    // For a symbol search box
    List<InstrumentEntity> findTop20ByTradingsymbolStartingWithIgnoreCase(String prefix);

    // Search box ranking: "NIFTY" matches thousands of options, so put the useful rows first:
    // exact symbol match, then indices, stocks, futures, options; NSE before BSE; nearest expiry first.
    // query must already be upper-cased; pass a Pageable to limit the result count.
    @Query("""
            SELECT i FROM InstrumentEntity i
            WHERE UPPER(i.tradingsymbol) LIKE CONCAT(:query, '%')
               OR UPPER(i.name) LIKE CONCAT(:query, '%')
            ORDER BY
                CASE WHEN UPPER(i.tradingsymbol) = :query THEN 0 ELSE 1 END,
                CASE
                    WHEN i.segment = 'INDICES' THEN 0
                    WHEN i.instrumentType = 'EQ' THEN 1
                    WHEN i.instrumentType = 'FUT' THEN 2
                    ELSE 3
                END,
                CASE i.exchange WHEN 'NSE' THEN 0 WHEN 'NFO' THEN 0 ELSE 1 END,
                i.expiry ASC NULLS FIRST,
                i.tradingsymbol
            """)
    List<InstrumentEntity> search(@Param("query") String query, Pageable pageable);
}
