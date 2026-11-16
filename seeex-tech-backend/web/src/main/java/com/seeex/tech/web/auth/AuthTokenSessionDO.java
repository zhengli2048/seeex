package com.seeex.tech.web.auth;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
@TableName("auth_token_session")
public class AuthTokenSessionDO {
 @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id; private String subjectId,clientId,accessTokenHash,refreshTokenHash,status; private LocalDateTime accessExpiresAt,refreshExpiresAt,createdAt,revokedAt; private Long rotatedFromId;
 public Long getId(){return id;} public void setId(Long v){id=v;} public String getSubjectId(){return subjectId;} public void setSubjectId(String v){subjectId=v;} public String getClientId(){return clientId;} public void setClientId(String v){clientId=v;} public String getAccessTokenHash(){return accessTokenHash;} public void setAccessTokenHash(String v){accessTokenHash=v;} public String getRefreshTokenHash(){return refreshTokenHash;} public void setRefreshTokenHash(String v){refreshTokenHash=v;} public LocalDateTime getAccessExpiresAt(){return accessExpiresAt;} public void setAccessExpiresAt(LocalDateTime v){accessExpiresAt=v;} public LocalDateTime getRefreshExpiresAt(){return refreshExpiresAt;} public void setRefreshExpiresAt(LocalDateTime v){refreshExpiresAt=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getRevokedAt(){return revokedAt;} public void setRevokedAt(LocalDateTime v){revokedAt=v;} public Long getRotatedFromId(){return rotatedFromId;} public void setRotatedFromId(Long v){rotatedFromId=v;}
 private Long accountId,accountVersion;
 /** 读取本地凭证来源账号。 */
 public Long getAccountId(){return accountId;}
 /** 设置本地凭证来源账号。 */
 public void setAccountId(Long value){accountId=value;}
 /** 读取已验证的凭证版本。 */
 public Long getAccountVersion(){return accountVersion;}
 /** 设置已验证的凭证版本。 */
 public void setAccountVersion(Long value){accountVersion=value;}
}
