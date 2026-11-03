package com.app.algodesk.controller;

import com.app.algodesk.service.UserProfileService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Errors are turned into JSON responses by ApiExceptionHandler
@RestController
public class UserContoller {

    private final UserProfileService userProfileService;

    public UserContoller(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    // Used by the navbar's account menu on every page.
    // (Replaces the old /api/dashboard; margins now come from /api/dashboard/summary.)
    @GetMapping(value = "/api/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public String profile() throws Exception {
        // Kite wraps every response as {"status": "...", "data": {...}}; keep only the data part
        JSONObject profile = new JSONObject(userProfileService.getProfileDetails()).getJSONObject("data");
        JSONArray exchanges = profile.optJSONArray("exchanges");
        return new JSONObject()
                .put("user_id", profile.optString("user_id"))
                .put("user_name", profile.optString("user_name"))
                .put("email", profile.optString("email"))
                .put("broker", profile.optString("broker"))
                .put("exchanges", exchanges != null ? exchanges : new JSONArray())
                .toString();
    }
}
