package com.group8.evcoownership.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cho GlobalExceptionHandler
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void testHandleMethodArgumentTypeMismatch() {
        // Tạo mock exception
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
            "[objectObject]", Long.class, "groupId", null, null
        );

        // Test handler
        ResponseEntity<Map<String, Object>> response = handler.handleMethodArgumentTypeMismatch(ex);

        // Verify response
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.BAD_REQUEST.value(), body.get("status"));
        assertEquals("Bad Request", body.get("error"));
        assertEquals("Invalid parameter type", body.get("message"));
        assertEquals("groupId", body.get("parameter"));
        assertEquals("Long", body.get("expectedType"));
        assertEquals("[objectObject]", body.get("actualValue"));
        
        // Verify suggestion for object error
        assertTrue(body.containsKey("suggestion"));
        assertTrue(body.get("suggestion").toString().contains("Frontend is sending an object"));
    }

    @Test
    void testHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid input");
        
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgumentException(ex);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.BAD_REQUEST.value(), body.get("status"));
        assertEquals("Bad Request", body.get("error"));
        assertEquals("Invalid input", body.get("message"));
    }

    @Test
    void testHandleIllegalStateException() {
        IllegalStateException ex = new IllegalStateException("Resource already exists");
        
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalStateException(ex);
        
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.CONFLICT.value(), body.get("status"));
        assertEquals("Conflict", body.get("error"));
        assertEquals("Resource already exists", body.get("message"));
    }

    @Test
    void testHandleEntityNotFoundException() {
        jakarta.persistence.EntityNotFoundException ex = new jakarta.persistence.EntityNotFoundException("Entity not found");
        
        ResponseEntity<Map<String, Object>> response = handler.handleEntityNotFoundException(ex);
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.NOT_FOUND.value(), body.get("status"));
        assertEquals("Not Found", body.get("error"));
        assertEquals("Entity not found", body.get("message"));
    }

    @Test
    void testHandleGenericException() {
        Exception ex = new RuntimeException("Unexpected error");
        
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), body.get("status"));
        assertEquals("Internal Server Error", body.get("error"));
        assertEquals("An unexpected error occurred", body.get("message"));
    }
}
