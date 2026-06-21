package com.im.media.dto;

import lombok.Data;

/**
 * 预签名结果：客户端用 uploadUrl 直传（HTTP PUT），完成后用 downloadUrl 作为消息 media_url。
 */
@Data
public class PresignResponse {
    /** 预签名上传地址（PUT）。 */
    private String uploadUrl;
    private String method;
    /** 对象在桶内的 key。 */
    private String objectKey;
    /** 上传完成后的访问地址（写入消息 media_url）。 */
    private String downloadUrl;
    private int expiresInSeconds;
}
