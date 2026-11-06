package com.clouddocs.document;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(Long id) {
        super("Không tìm thấy tài liệu với id = " + id);
    }
}
