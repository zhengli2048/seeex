package com.seeex.tech.web.wecom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/** 环境配置回退、加密持久化和只写密钥管理。 */
@Service
@RequiredArgsConstructor
public class WeComSettingsService {
    private static final long SETTINGS_ID = 1L;
    private static final int NONCE_BYTES = 12;
    private final WeComSettingsMapper mapper;
    private final ObjectMapper json;
    @Value("${wecom.settings-key:}") private String storageKey;
    @Value("${wecom.corp-id:}") private String corpId;
    @Value("${wecom.agent-id:}") private String agentId;
    @Value("${wecom.secret:}") private String secret;
    @Value("${wecom.token:}") private String token;
    @Value("${wecom.aes-key:}") private String aesKey;
    @Value("${wecom.redirect-uri:http://localhost:5173/login}") private String redirectUri;
    @Value("${wecom.login-enabled:false}") private boolean loginEnabled;

    /** 获取内部运行配置，禁止通过 HTTP 原样返回。 */
    public Settings current() {
        WeComSettingsDO row = mapper.selectById(SETTINGS_ID);
        if (row == null) { return new Settings(corpId, agentId, secret, token, aesKey, redirectUri, loginEnabled); }
        try { return json.readValue(crypt(false, Base64.getDecoder().decode(row.getEncryptedConfig())), Settings.class); }
        catch (Exception ex) { throw new BizException(ErrorCode.INTERNAL_ERROR, "企业微信配置无法解密"); }
    }
    /** 返回不包含任何密钥的配置视图。 */
    public ConfigView view() {
        Settings value = current();
        return new ConfigView(!value.corpId().isBlank() && !value.secret().isBlank(), "WE_COM", "/api/integrations/wecom/events", value.corpId(), value.agentId(), value.redirectUri(), value.loginEnabled(), !value.secret().isBlank(), !value.aesKey().isBlank());
    }
    /** 保存完整基本信息，密钥留空保留原值。 */
    @Transactional
    public ConfigView save(Settings command) {
        Settings previous = current();
        if (command.corpId() == null || command.corpId().isBlank() || command.agentId() == null || command.agentId().isBlank()) { throw new BizException(ErrorCode.VALIDATION_FAILED, "CorpID 和 AgentID 必填"); }
        try { var uri = java.net.URI.create(command.redirectUri()); if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) { throw new IllegalArgumentException(); } }
        catch (RuntimeException ex) { throw new BizException(ErrorCode.VALIDATION_FAILED, "登录回调地址须为 HTTP(S) 地址"); }
        Settings value = new Settings(command.corpId(), command.agentId(), keep(command.secret(), previous.secret()), keep(command.token(), previous.token()), keep(command.aesKey(), previous.aesKey()), command.redirectUri(), command.loginEnabled());
        if (!value.aesKey().isBlank() && value.aesKey().length() != 43) { throw new BizException(ErrorCode.VALIDATION_FAILED, "EncodingAESKey 须为 43 位"); }
        try {
            WeComSettingsDO row = new WeComSettingsDO(); row.setId(SETTINGS_ID); row.setEncryptedConfig(Base64.getEncoder().encodeToString(crypt(true, json.writeValueAsBytes(value))));
            if (mapper.selectById(SETTINGS_ID) == null) { mapper.insert(row); } else { mapper.updateById(row); }
        } catch (BizException ex) { throw ex; }
        catch (Exception ex) { throw new BizException(ErrorCode.INTERNAL_ERROR, "配置保存失败"); }
        return view();
    }
    private String keep(String value, String previous) { return value == null || value.isBlank() ? previous : value; }
    private byte[] crypt(boolean encrypt, byte[] value) throws Exception {
        if (storageKey.isBlank()) { throw new BizException(ErrorCode.VALIDATION_FAILED, "服务端尚未配置安全存储密钥"); }
        byte[] key;
        try { key = Base64.getDecoder().decode(storageKey); } catch (IllegalArgumentException ex) { throw new BizException(ErrorCode.VALIDATION_FAILED, "安全存储密钥格式错误"); }
        if (key.length != 32) { throw new BizException(ErrorCode.VALIDATION_FAILED, "安全存储密钥须为 Base64 编码的 32 字节密钥"); }
        byte[] nonce = new byte[NONCE_BYTES];
        if (encrypt) { new SecureRandom().nextBytes(nonce); } else { System.arraycopy(value, 0, nonce, 0, NONCE_BYTES); }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        if (encrypt) { byte[] encrypted = cipher.doFinal(value); return ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array(); }
        return cipher.doFinal(value, NONCE_BYTES, value.length - NONCE_BYTES);
    }
    /** 仅在服务内部流转的连接参数。 */
    public record Settings(String corpId, String agentId, String secret, String token, String aesKey, String redirectUri, boolean loginEnabled) { }
    /** 前端脱敏视图。 */
    public record ConfigView(boolean configured, String provider, String callbackPath, String corpId, String agentId, String redirectUri, boolean loginEnabled, boolean hasSecret, boolean hasAesKey) { }
}
