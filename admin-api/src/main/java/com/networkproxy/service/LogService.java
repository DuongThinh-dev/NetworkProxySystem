package com.networkproxy.service;

import com.networkproxy.dto.LogResponse;
import com.networkproxy.dto.PageResponse;
import com.networkproxy.entity.ConnectionLog;
import com.networkproxy.exception.ApiException;
import com.networkproxy.repository.LogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class LogService {

    private final LogRepository logRepository;

    public LogService(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public PageResponse<LogResponse> list(
            String clientIp,
            String target,
            String protocol,
            String decision,
            String executionStatus,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        String normalizedProtocol = normalizeProtocol(protocol);
        String normalizedDecision = normalizeDecision(decision);
        String normalizedStatus = normalizeStatus(executionStatus);

        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null
                ? null
                : to.plusDays(1).atStartOfDay();
        if (fromTime != null && toTime != null && !fromTime.isBefore(toTime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Khoảng thời gian không hợp lệ");
        }

        PageResponse<ConnectionLog> result = logRepository.findPage(
                clientIp,
                target,
                normalizedProtocol,
                normalizedDecision,
                normalizedStatus,
                fromTime,
                toTime,
                Pagination.page(page),
                Pagination.size(size));

        return new PageResponse<>(
                result.content().stream().map(LogResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    public LogResponse get(long id) {
        ConnectionLog log = logRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy log #" + id));
        return LogResponse.from(log);
    }

    private static String normalizeProtocol(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (!normalized.equals("HTTP")
                && !normalized.equals("HTTPS")
                && !normalized.equals("SSH")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Protocol không hợp lệ");
        }
        return normalized;
    }

    private static String normalizeDecision(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (!normalized.equals("ALLOW") && !normalized.equals("BLOCK")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Decision không hợp lệ");
        }
        return normalized;
    }

    private static String normalizeStatus(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (!normalized.equals("CONNECTED")
                && !normalized.equals("BLOCKED")
                && !normalized.equals("ERROR")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Execution status không hợp lệ");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
