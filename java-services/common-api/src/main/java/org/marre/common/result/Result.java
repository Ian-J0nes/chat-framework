package org.marre.common.result;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("成功");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /**
     * 202 Accepted：任务已受理，将异步处理。
     * 入参：data（可包含 request_id、sessionId 等回执信息）
     * 出参：Result<T>（code=202，message=accepted）
     */
    public static <T> Result<T> accepted(T data) {
        Result<T> result = new Result<>();
        result.setCode(202);
        result.setMessage("accepted");
        result.setData(data);
        return result;
    }
}
