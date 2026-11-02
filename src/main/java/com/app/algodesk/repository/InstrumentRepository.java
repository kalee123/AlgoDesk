package com.app.algodesk.repository;

import com.app.algodesk.entity.InstrumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
