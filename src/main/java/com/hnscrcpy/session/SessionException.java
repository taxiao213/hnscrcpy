package com.hnscrcpy.session;

/**
 * 会话层异常。message 面向用户，cause 保留原始堆栈。
 */
public class SessionException extends RuntimeException {

    public SessionException(String message) {
        super(message);
    }

    public SessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
