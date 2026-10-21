package com.ohcp.cropagent.api;

/**
 * 统一响应体。所有接口（含流式首帧）都用这个外层结构。
 * code=0 成功；非 0 见错误码约定。traceId 用于前后端联调定位。
 */
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;
    private long costMs;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data, String traceId, long costMs) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.costMs = costMs;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, null, 0);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, null, 0);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getCostMs() {
        return costMs;
    }

    public void setCostMs(long costMs) {
        this.costMs = costMs;
    }
}
