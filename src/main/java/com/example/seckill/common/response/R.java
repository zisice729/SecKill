package com.example.seckill.common.response;

import lombok.Data;

/**
 * 通用响应类
 */
@Data
public class R {

    private int code;
    private String msg;
    private Object data;

    private R(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static R success(String msg) {
        return new R(200, msg, null);
    }

    public static R success(String msg, Object data) {
        return new R(200, msg, data);
    }

    public static R fail(String msg) {
        return new R(500, msg, null);
    }

    public static R fail(int code, String msg) {
        return new R(code, msg, null);
    }
}
