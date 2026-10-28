package com.floworchestrator.controlcenter.error;

import com.floworchestrator.controlcenter.exception.ProcessNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");

    @Test
    @DisplayName("should hide internal details on unexpected errors")
    void shouldHideInternalDetails() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new RuntimeException("SQL error: password=secret"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiError body = response.getBody();
        assertNotNull(body);
        assertEquals(FlowCode.INTERNAL, body.flowCode());
        assertFalse(body.message().contains("SQL"));
        assertFalse(body.message().contains("secret"));
        assertEquals("/api/test", body.path());
        assertNotNull(body.timestamp());
    }

    @Test
    @DisplayName("should map domain exceptions to their status and flow code")
    void shouldMapDomainException() {
        UUID executionId = UUID.randomUUID();

        ResponseEntity<ApiError> response = handler.handleAppException(
                new ProcessNotFoundException(executionId), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ApiError body = response.getBody();
        assertNotNull(body);
        assertEquals(FlowCode.PROCESS_NOT_FOUND, body.flowCode());
        assertTrue(body.message().contains("No encontramos el proceso"));
    }

    @Test
    @DisplayName("should treat an SSE client disconnect as a non-error")
    void shouldIgnoreClientDisconnect() {
        handler.handleClientDisconnect(
                new AsyncRequestNotUsableException("ServletOutputStream failed to flush"),
                new MockHttpServletRequest("GET", "/api/processes/events"));
    }

    @Test
    @DisplayName("should map unsupported HTTP methods to 405")
    void shouldMapMethodNotAllowed() {
        ResponseEntity<ApiError> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PUT"), request);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals(FlowCode.METHOD_NOT_ALLOWED, response.getBody().flowCode());
    }

    @Test
    @DisplayName("should map unsupported media types to 415")
    void shouldMapUnsupportedMediaType() {
        ResponseEntity<ApiError> response = handler.handleUnsupportedMediaType(
                new HttpMediaTypeNotSupportedException("Unsupported media type"), request);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals(FlowCode.UNSUPPORTED_MEDIA_TYPE, response.getBody().flowCode());
    }

    @Test
    @DisplayName("should map unknown routes to 404")
    void shouldMapNoResourceFound() {
        ResponseEntity<ApiError> response = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "/missing"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(FlowCode.RESOURCE_NOT_FOUND, response.getBody().flowCode());
    }
}
