package io.tradeops.error;

import jakarta.servlet.http.HttpServletRequest;

import io.tradeops.web.CorrelationIdFilter;

public record ApiError(String code, String message, String correlationId) {
    public static ApiError of(String code, String message, HttpServletRequest request) {
        Object correlationId = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return new ApiError(code, message, correlationId == null ? "" : correlationId.toString());
    }
}
