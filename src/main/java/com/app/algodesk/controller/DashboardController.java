package com.app.algodesk.controller;

import com.app.algodesk.service.DashBoardService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
public class DashboardController {

    private final DashBoardService dashBoardService;
    public DashboardController(DashBoardService dashBoardService){
        this.dashBoardService = dashBoardService;
    }

    @GetMapping("/summary")
    public String detailedSummary() throws Exception {
        return dashBoardService.getDetailedSummary().toString();
    }


}
