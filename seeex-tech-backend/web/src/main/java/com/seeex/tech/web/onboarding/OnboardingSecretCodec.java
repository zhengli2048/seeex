package com.seeex.tech.web.onboarding;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
/** 首次交付独立AES-GCM密钥，绑定交付对象AAD，禁止明文回查。 */
@Component
public class OnboardingSecretCodec {
    private static final int NONCE_BYTES=12;
    @Value("${onboarding.delivery-key:}") private String key;
    @Value("${onboarding.delivery-key-id:v1}") private String keyId;
    /** 验证独立部署密钥，无开发默认值。 */
    public void requireConfigured() { material(); if(keyId==null || !keyId.matches("[A-Za-z0-9._-]{1,64}")) throw new BizException(ErrorCode.BUSINESS_ERROR,"交付密钥标识配置无效"); }
    /** 仅返回配置是否可用，不输出密钥。 */
    public boolean configured() { try { requireConfigured(); return true; } catch(BizException ex) { return false; } }
    /** 当前加密版本标识。 */
    public String keyId() { requireConfigured(); return keyId; }
    /** 每个交付使用随机nonce，密文包含nonce前缀。 */
    public String encrypt(String value,String aad) {
        requireConfigured();
        try { byte[] nonce=new byte[NONCE_BYTES]; new SecureRandom().nextBytes(nonce); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(material(),"AES"),new GCMParameterSpec(128,nonce)); cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8)); byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)); return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length+encrypted.length).put(nonce).put(encrypted).array()); }
        catch(java.security.GeneralSecurityException ex) { throw new BizException(ErrorCode.INTERNAL_ERROR,"交付加密失败"); }
    }
    /** 只能在完成对象ACL与CAS检查的领取事务中解密。 */
    public String decrypt(String value,String storedKeyId,String aad) {
        requireConfigured(); if(!keyId.equals(storedKeyId)) throw new BizException(ErrorCode.CONFLICT,"交付使用旧密钥版本，请联系平台办理人补发");
        try { byte[] bytes=Base64.getDecoder().decode(value); if(bytes.length<=NONCE_BYTES) throw new java.security.GeneralSecurityException(); byte[] nonce=java.util.Arrays.copyOf(bytes,NONCE_BYTES); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(material(),"AES"),new GCMParameterSpec(128,nonce)); cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8)); return new String(cipher.doFinal(bytes,NONCE_BYTES,bytes.length-NONCE_BYTES),StandardCharsets.UTF_8); }
        catch(java.security.GeneralSecurityException | IllegalArgumentException ex) { throw new BizException(ErrorCode.CONFLICT,"交付凭证不可读取，请联系平台办理人补发"); }
    }
    private byte[] material() { try { byte[] decoded=Base64.getDecoder().decode(key); if(decoded.length!=32) throw new IllegalArgumentException(); return decoded; } catch(IllegalArgumentException | NullPointerException ex) { throw new BizException(ErrorCode.BUSINESS_ERROR,"尚未配置独立的APP_DELIVERY_KEY（Base64编码32字节）"); } }
}
