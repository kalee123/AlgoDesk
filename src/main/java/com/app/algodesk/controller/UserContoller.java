package com.app.algodesk.controller;

import com.app.algodesk.service.UserProfileService;
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

    @GetMapping(value = "/api/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
    public String dashBoard() throws Exception {
        // Kite wraps every response as {"status": "...", "data": {...}}; keep only the data part
        JSONObject profile = new JSONObject(userProfileService.getProfileDetails()).getJSONObject("data");
        JSONObject margin = new JSONObject(userProfileService.getMarginDetails()).getJSONObject("data");

        JSONObject result = new JSONObject();
        result.put("profile_details", profile);
        result.put("margin_details", margin);
        return result.toString();
    }
}
