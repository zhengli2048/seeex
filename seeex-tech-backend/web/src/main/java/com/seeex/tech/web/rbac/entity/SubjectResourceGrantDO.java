package com.seeex.tech.web.rbac.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
@TableName("subject_resource_grant")
public class SubjectResourceGrantDO {
 @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id; private String subjectType; private Long subjectId; private Long resourceId; private String effect;
 public Long getId(){return id;} public void setId(Long v){id=v;} public String getSubjectType(){return subjectType;} public void setSubjectType(String v){subjectType=v;} public Long getSubjectId(){return subjectId;} public void setSubjectId(Long v){subjectId=v;} public Long getResourceId(){return resourceId;} public void setResourceId(Long v){resourceId=v;} public String getEffect(){return effect;} public void setEffect(String v){effect=v;}
}
