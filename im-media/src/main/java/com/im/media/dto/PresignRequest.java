package com.im.media.dto;

import lombok.Data;

/**
 * 申请上传预签名。
 */
@Data
public class PresignRequest {
    /** 原始文件名（用于推断扩展名）。 */
    private String filename;
    /** MIME 类型，可空。 */
    private String contentType;
}
