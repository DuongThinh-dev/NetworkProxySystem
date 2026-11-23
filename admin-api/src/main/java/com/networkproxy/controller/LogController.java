package com.networkproxy.controller;

import com.networkproxy.dto.LogResponse;
import com.networkproxy.dto.PageResponse;
import com.networkproxy.service.LogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public PageResponse<LogResponse> list(
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String executionStatus,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return logService.list(
                clientIp,
                target,
                protocol,
                decision,
                executionStatus,
                from,
                to,
                page,
                size);
    }

    @GetMapping("/{id}")
    public LogResponse get(@PathVariable long id) {
        return logService.get(id);
    }
}
