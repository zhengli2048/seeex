package com.seeex.tech.web.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** RBAC 可授权资源。 */
@TableName("resource")
public class ResourceDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Long id;
    private String code;
    private String permissionCode;
    private String name;
    private String resourceType;
    private String status;
    private String applicationCode;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long parentId;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String routePath;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String icon;
    private Integer sortOrder;
    private Boolean visible;
    @TableField("is_public")
    private Boolean publicResource;
    private java.time.LocalDateTime deletedAt;

    /** 读取Id 字段。 */
    public Long getId() { return id; }
    /** 设置Id 字段。 */
    public void setId(Long id) { this.id = id; }
    /** 读取Code 字段。 */
    public String getCode() { return code; }
    /** 设置Code 字段。 */
    public void setCode(String code) { this.code = code; }
    /** 读取PermissionCode 字段。 */
    public String getPermissionCode() { return permissionCode; }
    /** 设置PermissionCode 字段。 */
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    /** 读取Name 字段。 */
    public String getName() { return name; }
    /** 设置Name 字段。 */
    public void setName(String name) { this.name = name; }
    /** 读取ResourceType 字段。 */
    public String getResourceType() { return resourceType; }
    /** 设置ResourceType 字段。 */
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    /** 读取Status 字段。 */
    public String getStatus() { return status; }
    /** 设置Status 字段。 */
    public void setStatus(String status) { this.status = status; }
    /** 读取ApplicationCode 字段。 */
    public String getApplicationCode() { return applicationCode; }
    /** 设置ApplicationCode 字段。 */
    public void setApplicationCode(String applicationCode) { this.applicationCode = applicationCode; }
    /** 读取ParentId 字段。 */
    public Long getParentId() { return parentId; }
    /** 设置ParentId 字段。 */
    public void setParentId(Long parentId) { this.parentId = parentId; }
    /** 读取RoutePath 字段。 */
    public String getRoutePath() { return routePath; }
    /** 设置RoutePath 字段。 */
    public void setRoutePath(String routePath) { this.routePath = routePath; }
    /** 读取Icon 字段。 */
    public String getIcon() { return icon; }
    /** 设置Icon 字段。 */
    public void setIcon(String icon) { this.icon = icon; }
    /** 读取SortOrder 字段。 */
    public Integer getSortOrder() { return sortOrder; }
    /** 设置SortOrder 字段。 */
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    /** 读取Visible 字段。 */
    public Boolean getVisible() { return visible; }
    /** 设置Visible 字段。 */
    public void setVisible(Boolean visible) { this.visible = visible; }
    /** 读取PublicResource 字段。 */
    public Boolean getPublicResource() { return publicResource; }
    /** 设置PublicResource 字段。 */
    public void setPublicResource(Boolean publicResource) { this.publicResource = publicResource; }
    /** 读取DeletedAt 字段。 */
    public java.time.LocalDateTime getDeletedAt() { return deletedAt; }
    /** 设置DeletedAt 字段。 */
    public void setDeletedAt(java.time.LocalDateTime v) { deletedAt = v; }
    private String entryType;
    /** 读取资源所属端。 */
    public String getEntryType() { return entryType; }
    /** 设置资源所属端。 */
    public void setEntryType(String value) { entryType = value; }
    private String menuCategory;
    /** 读取菜单分类。 */
    public String getMenuCategory() { return menuCategory; }
    /** 设置菜单分类。 */
    public void setMenuCategory(String value) { menuCategory = value; }
}
