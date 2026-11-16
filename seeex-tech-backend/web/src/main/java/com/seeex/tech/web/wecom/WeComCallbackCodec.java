package com.seeex.tech.web.wecom;

import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class WeComCallbackCodec {
    private final WeComSettingsService settings;
    public WeComCallbackCodec(WeComSettingsService settings) { this.settings = settings; }

    public String decrypt(String signature, String timestamp, String nonce, String encrypted) {
        verify(signature, timestamp, nonce, encrypted);
        try {
            byte[] key = Base64.getDecoder().decode(settings.current().aesKey() + "=");
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new javax.crypto.spec.IvParameterSpec(Arrays.copyOf(key, 16)));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            if (plain.length < 32) { throw new BizException(ErrorCode.VALIDATION_FAILED, "回调密文过短"); }
            int padding = plain[plain.length - 1] & 0xff;
            if (padding < 1 || padding > 32 || padding > plain.length) { throw new BizException(ErrorCode.VALIDATION_FAILED, "回调填充错误"); }
            for (int index = plain.length - padding; index < plain.length; index++) { if ((plain[index] & 0xff) != padding) { throw new BizException(ErrorCode.VALIDATION_FAILED, "回调填充错误"); } }
            plain = Arrays.copyOf(plain, plain.length - padding);
            int messageLength = ByteBuffer.wrap(plain, 16, 4).getInt();
            if (messageLength < 0 || messageLength > plain.length - 20) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "invalid WeCom message length");
            }
            String receiver = new String(plain, 20 + messageLength, plain.length - 20 - messageLength, StandardCharsets.UTF_8);
            if (!settings.current().corpId().equals(receiver)) { throw new BizException(ErrorCode.FORBIDDEN, "回调企业标识不匹配"); }
            return new String(plain, 20, messageLength, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "unable to decrypt WeCom callback");
        }
    }

    public void verify(String signature, String timestamp, String nonce, String encrypted) {
        if (settings.current().token().isBlank() || settings.current().aesKey().isBlank() || settings.current().corpId().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "WeCom callback security settings are not configured");
        }
        try {
            String[] values = {settings.current().token(), timestamp, nonce, encrypted};
            Arrays.sort(values);
            String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(String.join("", values).getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                throw new BizException(ErrorCode.FORBIDDEN, "invalid WeCom callback signature");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "unable to verify WeCom callback");
        }
    }

    public String decryptEcho(String signature, String timestamp, String nonce, String echostr) {
        return decrypt(signature, timestamp, nonce, echostr);
    }
}
