package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;

import java.io.OutputStream;

/**
 * 有上限的输出流：超过上限立即失败，避免异常响应耗尽内存。
 */
final class LimitedOutputStream extends OutputStream {

    private final OutputStream delegate;

    private final long limit;

    private long written;

    LimitedOutputStream(OutputStream delegate, long limit) {
        this.delegate = delegate;
        this.limit = limit;
    }

    @Override
    public void write(int b) throws java.io.IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws java.io.IOException {
        if (written + len > limit) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "资源内容超出读取上限");
        }
        written += len;
        delegate.write(b, off, len);
    }

    @Override
    public void flush() throws java.io.IOException {
        delegate.flush();
    }

    @Override
    public void close() throws java.io.IOException {
        delegate.close();
    }
}
