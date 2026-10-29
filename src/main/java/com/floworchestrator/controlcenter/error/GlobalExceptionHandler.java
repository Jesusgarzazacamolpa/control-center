package com.floworchestrator.controlcenter.error;

import com.floworchestrator.controlcenter.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Traduce cualquier excepción a un contrato de error estable, con mensaje
 * accionable para la UI y un código de flujo para soporte. Los detalles técnicos
 * y el StackTrace solo se escriben en los logs.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String VALIDATION_MESSAGE = "Revisa los datos ingresados: ";
    private static final String GENERIC_VALIDATION_MESSAGE = "Revisa los datos ingresados e intenta nuevamente.";

    /**
     * Traduce las excepciones de dominio que ya traen estatus, código de flujo y
     * mensaje para el usuario.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleAppException(AppException ex, HttpServletRequest request) {
        logHandled(ex.getStatus(), ex.getFlowCode(), ex, request);
        return respond(ex.getStatus(), ex.getFlowCode(), ex.getUserMessage(), null, request);
    }

    /**
     * Convierte los errores de {@code @Valid} en una lista de campos inválidos.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldViolation> details = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                details.add(new ApiError.FieldViolation(error.getField(), error.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                details.add(new ApiError.FieldViolation(error.getObjectName(), error.getDefaultMessage())));

        String message = details.isEmpty()
                ? GENERIC_VALIDATION_MESSAGE
                : VALIDATION_MESSAGE + details.get(0).message();
        logHandled(HttpStatus.BAD_REQUEST, FlowCode.VALIDATION, ex, request);
        return respond(HttpStatus.BAD_REQUEST, FlowCode.VALIDATION, message, details, request);
    }

    /**
     * Convierte las violaciones de restricciones de parámetros (p. ej. {@code @Min})
     * en el mismo contrato de validación.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<ApiError.FieldViolation> details = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldViolation(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        String message = details.isEmpty()
                ? GENERIC_VALIDATION_MESSAGE
                : VALIDATION_MESSAGE + details.get(0).message();
        logHandled(HttpStatus.BAD_REQUEST, FlowCode.VALIDATION, ex, request);
        return respond(HttpStatus.BAD_REQUEST, FlowCode.VALIDATION, message, details, request);
    }

    /**
     * Responde 400 cuando el cuerpo no es JSON válido o contiene valores no permitidos.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        logHandled(HttpStatus.BAD_REQUEST, FlowCode.BAD_REQUEST, ex, request);
        return respond(HttpStatus.BAD_REQUEST, FlowCode.BAD_REQUEST,
                "El cuerpo de la solicitud no es válido. Verifica el formato JSON y que los valores enviados estén permitidos.",
                null, request);
    }

    /**
     * Responde 400 con un mensaje específico cuando un path variable o query param
     * no se puede convertir al tipo esperado (por ejemplo, un UUID mal formado).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        String message = UUID.class.equals(ex.getRequiredType())
                ? "El identificador proporcionado no es válido. Debe tener formato UUID (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)."
                : "El valor proporcionado para '" + ex.getName() + "' no es válido. Verifica e intenta nuevamente.";
        logHandled(HttpStatus.BAD_REQUEST, FlowCode.BAD_REQUEST, ex, request);
        return respond(HttpStatus.BAD_REQUEST, FlowCode.BAD_REQUEST, message, null, request);
    }

    /**
     * Responde 400 indicando el nombre del parámetro obligatorio que falta.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                           HttpServletRequest request) {
        logHandled(HttpStatus.BAD_REQUEST, FlowCode.BAD_REQUEST, ex, request);
        return respond(HttpStatus.BAD_REQUEST, FlowCode.BAD_REQUEST,
                "Falta el parámetro obligatorio '" + ex.getParameterName() + "'. Inclúyelo e intenta nuevamente.",
                null, request);
    }

    /**
     * Responde 405 cuando el verbo HTTP no está mapeado en la ruta.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        logHandled(HttpStatus.METHOD_NOT_ALLOWED, FlowCode.METHOD_NOT_ALLOWED, ex, request);
        return respond(HttpStatus.METHOD_NOT_ALLOWED, FlowCode.METHOD_NOT_ALLOWED,
                "El método HTTP utilizado no está permitido para esta ruta. Verifica la operación e intenta nuevamente.",
                null, request);
    }

    /**
     * Responde 415 cuando el {@code Content-Type} no es {@code application/json}.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                               HttpServletRequest request) {
        logHandled(HttpStatus.UNSUPPORTED_MEDIA_TYPE, FlowCode.UNSUPPORTED_MEDIA_TYPE, ex, request);
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, FlowCode.UNSUPPORTED_MEDIA_TYPE,
                "El tipo de contenido no está soportado. Envía la solicitud como application/json.",
                null, request);
    }

    /**
     * Responde 404 cuando la ruta solicitada no existe.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        logHandled(HttpStatus.NOT_FOUND, FlowCode.RESOURCE_NOT_FOUND, ex, request);
        return respond(HttpStatus.NOT_FOUND, FlowCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no existe. Verifica la ruta e intenta nuevamente.",
                null, request);
    }

    /**
     * El cliente cerró la conexión SSE (recarga, navegación, timeout del proxy).
     * No es un error del servidor y la respuesta ya no es utilizable.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(AsyncRequestNotUsableException ex, HttpServletRequest request) {
        log.debug("[SSE] El cliente cerró la conexión: path={}, error={}",
                request != null ? request.getRequestURI() : "unknown", ex.getMessage());
    }

    /**
     * Red de seguridad: registra el stacktrace y responde 500 con un mensaje genérico.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        logHandled(HttpStatus.INTERNAL_SERVER_ERROR, FlowCode.INTERNAL, ex, request);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, FlowCode.INTERNAL,
                "Ocurrió un error inesperado. Intenta nuevamente en unos minutos; si persiste, contacta a soporte con el código de flujo.",
                null, request);
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String flowCode, String message,
                                             List<ApiError.FieldViolation> details,
                                             HttpServletRequest request) {
        ApiError body = new ApiError(
                flowCode,
                message,
                MDC.get("traceId"),
                OffsetDateTime.now(ZoneOffset.UTC),
                request != null ? request.getRequestURI() : null,
                details == null || details.isEmpty() ? null : List.copyOf(details));
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Los 5xx se registran con stacktrace; los 4xx solo como advertencia.
     */
    private void logHandled(HttpStatus status, String flowCode, Exception ex, HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "unknown";
        if (status.is5xxServerError()) {
            log.error("[{}] path={} error={}", flowCode, path, ex.getMessage(), ex);
        } else {
            log.warn("[{}] path={} error={}", flowCode, path, ex.getMessage());
        }
    }
}
