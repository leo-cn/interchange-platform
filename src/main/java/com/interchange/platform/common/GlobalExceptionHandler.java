package com.interchange.platform.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。保证前端任何异常都能拿到统一结构，而不是 500 白页。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ResponseEntity.ok(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<R<Void>> handleValid(Exception e) {
        String msg;
        if (e instanceof MethodArgumentNotValidException ex) {
            msg = ex.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining("；"));
        } else {
            BindException ex = (BindException) e;
            msg = ex.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining("；"));
        }
        return ResponseEntity.ok(R.fail(400, "参数校验失败: " + msg));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<R<Void>> handleNotFound(NoHandlerFoundException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(404, "接口不存在: " + request.getRequestURI()));
    }

    /**
     * 请求了不存在的地址（页面或接口）。
     * 这是正常的 404，不是系统故障——按 warn 记一行即可，
     * 不要打完整堆栈，否则浏览器请求 favicon.ico 之类的噪音会淹掉日志。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("请求的资源不存在: {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(404, "资源不存在: " + request.getRequestURI()));
    }

    /**
     * 路径参数/查询参数类型不匹配，例如把 "/api/log/list" 当成 "/api/log/{id}" 访问，
     * "list" 无法转成 Long。属于调用方写法问题，返回 400 提示即可，不必记 error 堆栈。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                      HttpServletRequest request) {
        log.warn("参数类型不匹配: {} {} 参数[{}]",
                request.getMethod(), request.getRequestURI(), e.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(400, "参数不合法: " + e.getName() + " 类型不正确"));
    }

    /**
     * 缺少必填请求参数。
     * 常见于前端只发了 GET 或请求体为空（表单字段全丢）的情况，
     * 返回一句人话提示即可，不要把 Spring 内部异常信息甩到界面上。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParam(MissingServletRequestParameterException e,
                                                      HttpServletRequest request) {
        log.warn("缺少请求参数: {} {} 参数[{}]",
                request.getMethod(), request.getRequestURI(), e.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(400, "缺少必要参数：" + e.getParameterName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleOther(Exception e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        log.error("系统异常 uri={} ", uri, e);
        // 对外只给一句通用提示 + traceId，内部异常细节不外泄（浏览器控制台看得到，体验很差）
        String traceId = R.traceId();
        log.error("系统异常 traceId={} uri={} msg={}", traceId, uri, e.toString());
        return ResponseEntity.ok(R.fail(500, "系统开小差了，请稍后重试（追踪号 " + traceId + "）"));
    }
}
