package com.seeex.tech.web.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

@Component
public class FileServiceClient {
    private final RestClient client;
    private final String token;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public FileServiceClient(
            @Value("${file-service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${file-service.token:}") String token, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.token = token; this.json = json;
    }

    /** 代理文件列表请求。 */
    public Map<String, Object> list(int page, int pageSize, String keyword, String status, String contentType, String owner, String tenant) {
        return call(() -> client.get().uri(uriBuilder -> uriBuilder.path("/api/files")
                        .queryParam("page", page).queryParam("page_size", pageSize)
                        .queryParamIfPresent("keyword", java.util.Optional.ofNullable(keyword))
                        .queryParamIfPresent("status", java.util.Optional.ofNullable(status))
                        .queryParamIfPresent("contentType", java.util.Optional.ofNullable(contentType)).build())
                .headers(headers -> serviceHeaders(headers, owner, tenant)).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { }));
    }

    /** 代理文件上传 URL 请求。 */
    public Map<String, Object> requestUploadUrl(String name, String type, long size, String idempotencyKey,
                                                String owner, String tenant) {
        return call(() -> client.post().uri(uriBuilder -> uriBuilder.path("/api/files/upload-url")
                        .queryParam("fileName", name).queryParam("contentType", type).queryParam("size", size).build())
                .headers(headers -> { serviceHeaders(headers, owner, tenant); headers.set("Idempotency-Key", idempotencyKey); })
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { }));
    }

    /** 代理上传确认请求。 */
    public Map<String, Object> confirm(String objectKey, String owner, String tenant) {
        return call(() -> client.post().uri(uriBuilder -> uriBuilder.path("/api/files/confirm").queryParam("objectKey", objectKey).build())
                .headers(headers -> serviceHeaders(headers, owner, tenant)).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { }));
    }

    /** 代理下载 URL 请求。 */
    public Map<String, Object> downloadUrl(String objectKey, String owner, String tenant) {
        return call(() -> client.get().uri(uriBuilder -> uriBuilder.path("/api/files/download-url").queryParam("objectKey", objectKey).build())
                .headers(headers -> serviceHeaders(headers, owner, tenant)).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { }));
    }

    /** 代理批量删除请求。 */
    public Map<String, Object> delete(List<String> objectKeys, String owner, String tenant) {
        return call(() -> client.method(HttpMethod.DELETE).uri("/api/files/batch").headers(headers -> serviceHeaders(headers, owner, tenant))
                .body(objectKeys).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { }));
    }

    private Map<String, Object> call(java.util.function.Supplier<Map<String, Object>> action) {
        try { return action.get(); }
        catch (org.springframework.web.client.RestClientResponseException ex) {
            try {
                var response = json.readTree(ex.getResponseBodyAsString());
                var code = com.seeex.tech.common.api.ErrorCode.valueOf(response.path("code").asText());
                throw new com.seeex.tech.common.exception.BizException(code, response.path("message").asText(code.defaultMessage()));
            } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException invalid) {
                throw new com.seeex.tech.common.exception.BizException(com.seeex.tech.common.api.ErrorCode.BUSINESS_ERROR, "文件服务请求失败");
            }
        } catch (org.springframework.web.client.RestClientException ex) { throw new com.seeex.tech.common.exception.BizException(com.seeex.tech.common.api.ErrorCode.BUSINESS_ERROR, "文件服务暂不可用"); }
    }

    private void serviceHeaders(org.springframework.http.HttpHeaders headers, String owner, String tenant) {
        headers.set("X-Service-Token", token);
        String app = "platform";
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attrs) { Object selected = attrs.getRequest().getAttribute("seeex.file.application"); if (selected instanceof String verified) app = verified; }
        headers.set("X-App-Code", app);
        headers.set("X-User-Id", owner);
        headers.set("X-Tenant-Id", tenant);
    }
}
