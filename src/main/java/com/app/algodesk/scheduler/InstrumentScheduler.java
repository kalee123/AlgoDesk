package com.app.algodesk.scheduler;

import com.app.algodesk.authentication.UserAuthService;
import com.app.algodesk.service.InstrumentDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

// Refreshes the instrument list every weekday morning.
// Runs on Spring's scheduler thread, not in a request, so RqInfo can't be used here;
// the token comes from the DB instead.
@Component
public class InstrumentScheduler {

    private static final Logger log = LoggerFactory.getLogger(InstrumentScheduler.class);

    @Autowired
    InstrumentDataService instrumentDataService;

    @Autowired
    UserAuthService userAuthService;

    @Scheduled(cron = "0 30 8 * * MON-FRI", zone = "Asia/Kolkata")
    public void refreshInstruments() {
        // Kite tokens die at 6 AM and can't be renewed without a manual login,
        // so if nobody has logged in yet today there is nothing to do
        Optional<String> accessToken = userAuthService.getAnyActiveAccessToken();
        if (accessToken.isEmpty()) {
            log.info("No active Kite session, skipping instrument refresh");
            return;
        }

        instrumentDataService.refreshIfNeeded(accessToken.get());
    }
}
