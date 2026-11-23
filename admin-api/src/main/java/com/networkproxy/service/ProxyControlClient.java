package com.networkproxy.service;

import com.networkproxy.config.ProxyControlProperties;
import com.networkproxy.dto.ProxyStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ProxyControlClient {

    private static final Logger log =
            LoggerFactory.getLogger(ProxyControlClient.class);
    private static final String TOKEN_HEADER = "X-Admin-Token";

    private final ProxyControlProperties properties;
    private final RestClient restClient;

    public ProxyControlClient(ProxyControlProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(3_000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.url())
                .build();
    }

    public ProxyStats stats() {
        if (!properties.enabled()) {
            return ProxyStats.offline("Chưa bật proxy control");
        }
        try {
            ProxyStatsResponse response = restClient.get()
                    .uri("/admin/stats")
                    .header(TOKEN_HEADER, properties.token())
                    .retrieve()
                    .body(ProxyStatsResponse.class);
            if (response == null) {
                return ProxyStats.offline("Proxy không trả về dữ liệu");
            }
            return new ProxyStats(
                    true,
                    response.activeClients(),
                    response.loadedRules(),
                    response.uptimeSeconds(),
                    response.version(),
                    null);
        } catch (Exception exception) {
            log.warn("Không kết nối được proxy control API: {}",
                    exception.getMessage());
            return ProxyStats.offline("Không kết nối được proxy control");
        }
    }

    public String reloadRules() {
        if (!properties.enabled()) {
            return "Chưa bật proxy control; khởi động lại proxy để áp dụng rule mới.";
        }
        try {
            ProxyReloadResponse response = restClient.post()
                    .uri("/admin/reload-rules")
                    .header(TOKEN_HEADER, properties.token())
                    .retrieve()
                    .body(ProxyReloadResponse.class);
            if (response == null || !"ok".equalsIgnoreCase(response.status())) {
                return "Proxy không xác nhận reload rule.";
            }
            return null;
        } catch (Exception exception) {
            log.warn("Không reload được rule trên proxy: {}",
                    exception.getMessage());
            return "Không thể reload proxy: " + exception.getMessage();
        }
    }

    public record ProxyStatsResponse(
            int activeClients,
            int loadedRules,
            long uptimeSeconds,
            String version
    ) {
    }

    public record ProxyReloadResponse(
            String status,
            int rules
    ) {
    }
}
