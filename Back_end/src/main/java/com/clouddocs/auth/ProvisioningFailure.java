package com.clouddocs.auth;

public class ProvisioningFailure extends RuntimeException {
    private final int status;
    public ProvisioningFailure(int status, Throwable cause) {
        super("Không thể liên kết hồ sơ. Vui lòng thử lại hoặc liên hệ quản trị viên.", cause);
        this.status = status;
    }
    public int status() { return status; }
}
