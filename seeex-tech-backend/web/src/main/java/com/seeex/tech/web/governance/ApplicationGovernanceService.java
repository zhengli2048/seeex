package com.seeex.tech.web.governance;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.organization.mapper.*;
import com.seeex.tech.web.rbac.mapper.*;
import com.seeex.tech.web.registry.ApplicationRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 应用管理员与资源策略管理，写入前验证应用和所有关联主体。 */
@Service
@RequiredArgsConstructor
public class ApplicationGovernanceService {
    private final ApplicationAdminMapper admins;
    private final ApplicationPolicyMapper policies;
    private final EmployeeMapper employees;
    private final DepartmentMapper departments;
    private final RoleMapper roles;
    private final ResourceMapper resources;
    private final ApplicationRegistryService registry;
    private final ObjectMapper json;
    private final ConfigurationRevisionService revisions;

    /** 查询管理员关联。 */
    public List<ApplicationAdminDO> admins(String app) { registry.detail(app); return admins.selectList(Wrappers.<ApplicationAdminDO>lambdaQuery().eq(ApplicationAdminDO::getApplicationCode, app)); }
    /** 同一数据库快照读取管理员与版本，避免旧集合配上新版本。 */
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public AdminSnapshot adminSnapshot(String app) { long version=revisions.current("app-admin:"+app); return new AdminSnapshot(admins(app),version); }
    /** 管理员原子读取结果。 */
    public record AdminSnapshot(List<ApplicationAdminDO> items,long version) { }
    /** 平台授权调用者整体替换应用管理员。 */
    @Transactional
    public void replaceAdmins(String app, AdminCommand command) {
        registry.detail(app);
        List<Long> ids=command.employeeIds();
        if (ids == null || ids.size() > 100 || new HashSet<>(ids).size() != ids.size()) { invalid("管理员列表无效或重复"); }
        validEmployees(new HashSet<>(ids));
        revisions.advance("app-admin:"+app,command.expectedVersion());
        admins.delete(Wrappers.<ApplicationAdminDO>lambdaQuery().eq(ApplicationAdminDO::getApplicationCode, app));
        for (Long id : ids) { var row = new ApplicationAdminDO(); row.setApplicationCode(app); row.setEmployeeId(id); admins.insert(row); }
    }
    /** 读取全部策略，包含停用策略以便恢复。 */
    public List<ApplicationPolicyDO> policies(String app) { registry.detail(app); return policies.selectList(Wrappers.<ApplicationPolicyDO>lambdaQuery().eq(ApplicationPolicyDO::getApplicationCode, app).orderByDesc(ApplicationPolicyDO::getId)); }
    /** 保存策略，ID与应用不可串用。 */
    @Transactional
    public ApplicationPolicyDO save(String app, Long id, PolicyCommand command) {
        registry.detail(app);
        if ("platform".equals(app)) { invalid("基础平台使用角色授权管理"); }
        if (command.name() == null || command.name().isBlank() || command.name().length() > 128) { invalid("策略名称必填且最多128字"); }
        if (!List.of("EMPLOYEE", "ROLE").contains(command.subjectType() == null ? "" : command.subjectType())) { invalid("策略主体类型无效"); }
        if ("EMPLOYEE".equals(command.subjectType())) { employee(command.subjectId()); }
        else { var role = command.subjectId() == null ? null : roles.selectById(command.subjectId()); if (role == null || role.getDeletedAt() != null || !"ACTIVE".equals(role.getStatus())) { invalid("角色不存在或已停用"); } }
        var resource = command.resourceId() == null ? null : resources.selectById(command.resourceId());
        if (resource == null || resource.getDeletedAt() != null || !"ACTIVE".equals(resource.getStatus()) || !app.equals(resource.getApplicationCode())) { invalid("请选择本应用有效资源"); }
        if (!List.of("ALLOW", "DENY").contains(command.effect() == null ? "" : command.effect()) || !List.of("SELF", "CUSTOM", "DEPARTMENT", "DEPARTMENT_AND_DESCENDANTS", "ALL").contains(command.scopeType() == null ? "" : command.scopeType()) || !List.of("ACTIVE", "INACTIVE").contains(command.status() == null ? "" : command.status())) { invalid("授权效果、数据范围或状态无效"); }
        Set<Long> departmentIds = command.departmentIds() == null ? Set.of() : command.departmentIds();
        Set<Long> employeeIds = command.employeeIds() == null ? Set.of() : command.employeeIds();
        if (departmentIds.size() + employeeIds.size() > 1000) { invalid("自定义范围最多1000项"); }
        if(departmentIds.stream().anyMatch(Objects::isNull) || !departmentIds.isEmpty() && departments.selectCount(Wrappers.<com.seeex.tech.web.organization.entity.DepartmentDO>lambdaQuery().in(com.seeex.tech.web.organization.entity.DepartmentDO::getId,departmentIds).isNull(com.seeex.tech.web.organization.entity.DepartmentDO::getDeletedAt).eq(com.seeex.tech.web.organization.entity.DepartmentDO::getStatus,"ACTIVE"))!=departmentIds.size()) invalid("范围部门无效");
        validEmployees(employeeIds);
        var row = id == null ? new ApplicationPolicyDO() : policies.selectById(id);
        if (row == null || id != null && !app.equals(row.getApplicationCode())) { invalid("策略不存在或不属于该应用"); }
        row.setApplicationCode(app); row.setName(command.name()); row.setSubjectType(command.subjectType()); row.setSubjectId(command.subjectId()); row.setResourceId(command.resourceId()); row.setEffect(command.effect()); row.setScopeType(command.scopeType()); row.setStatus(command.status());
        try { row.setScopeConfig(json.writeValueAsString(Map.of("departmentIds", departmentIds, "employeeIds", employeeIds))); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { invalid("范围配置无效"); }
        if (id == null) { row.setRowVersion(0L); policies.insert(row); }
        else {
            if(command.expectedVersion()==null || command.expectedVersion()<0 || command.expectedVersion()==Long.MAX_VALUE) invalid("expectedVersion必填");
            row.setRowVersion(command.expectedVersion()+1);
            if(policies.update(row,Wrappers.<ApplicationPolicyDO>lambdaQuery().eq(ApplicationPolicyDO::getId,id).eq(ApplicationPolicyDO::getApplicationCode,app).eq(ApplicationPolicyDO::getRowVersion,command.expectedVersion()))!=1) throw new BizException(ErrorCode.CONFLICT,"策略已发生变化，请重新载入并确认草稿");
        } return row;
    }
    /** 删除策略，立即影响后续权限计算。 */
    public void delete(String app, long id,Long expectedVersion) {
        registry.detail(app); if(expectedVersion==null || expectedVersion<0) invalid("expectedVersion必填");
        if(policies.delete(Wrappers.<ApplicationPolicyDO>lambdaQuery().eq(ApplicationPolicyDO::getId,id).eq(ApplicationPolicyDO::getApplicationCode,app).eq(ApplicationPolicyDO::getRowVersion,expectedVersion))!=1) throw new BizException(ErrorCode.CONFLICT,"策略已变化或已删除，请重新载入");
    }
    /** 管理员集合版本。 */
    public long adminVersion(String app) { return revisions.current("app-admin:"+app); }
    /** 管理员集合提交。 */
    public record AdminCommand(List<Long> employeeIds,Long expectedVersion) { }
    /** 只读取本应用指定资源，权限计算不扫描字典。 */
    public com.seeex.tech.web.rbac.entity.ResourceDO resource(String app,long id) {
        var row=resources.selectById(id);
        if(row==null || row.getDeletedAt()!=null || !"ACTIVE".equals(row.getStatus()) || !app.equals(row.getApplicationCode())) invalid("本应用资源无效");
        return row;
    }
    /** 首屏只取50个人员/角色；已配置的主体名称额外回填。树仅投影必要字段。 */
    public Map<String,Object> options(String app) { return options(app,new OptionQuery(null,List.of(),List.of())); }
    /** 远程搜索人员/角色；已选项单独回填，避免分页后标签丢失。 */
    public Map<String,Object> options(String app,OptionQuery filter) {
        registry.detail(app);
        String keyword=filter.keyword()==null?"":filter.keyword().trim();
        if(keyword.length()>128) invalid("搜索内容最多128字");
        Set<Long> employeeIds=selected(filter.employeeIds()), roleIds=selected(filter.roleIds());
        admins(app).forEach(row->employeeIds.add(row.getEmployeeId()));
        for(var policy:policies(app)) {
            if("EMPLOYEE".equals(policy.getSubjectType())) employeeIds.add(policy.getSubjectId()); else roleIds.add(policy.getSubjectId());
            try { var config=json.readTree(policy.getScopeConfig()==null?"{}":policy.getScopeConfig()); var ids=config.get("employeeIds"); if(ids!=null && ids.isArray()) ids.forEach(id->{ if(id.canConvertToLong()) employeeIds.add(id.longValue()); }); }
            catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new BizException(ErrorCode.VALIDATION_FAILED,"已保存策略范围格式无效，请修复后重试"); }
        }
        var employeeQuery=Wrappers.<com.seeex.tech.web.organization.entity.EmployeeDO>lambdaQuery().select(com.seeex.tech.web.organization.entity.EmployeeDO::getId,com.seeex.tech.web.organization.entity.EmployeeDO::getDisplayName).isNull(com.seeex.tech.web.organization.entity.EmployeeDO::getDeletedAt).eq(com.seeex.tech.web.organization.entity.EmployeeDO::getStatus,"ACTIVE").like(!keyword.isEmpty(),com.seeex.tech.web.organization.entity.EmployeeDO::getDisplayName,keyword).orderByAsc(com.seeex.tech.web.organization.entity.EmployeeDO::getId);
        Map<Long,Option> people=new LinkedHashMap<>();
        employees.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,50,false),employeeQuery).getRecords().forEach(row->people.put(row.getId(),new Option(row.getId(),row.getDisplayName())));
        if(!employeeIds.isEmpty()) employees.selectList(Wrappers.<com.seeex.tech.web.organization.entity.EmployeeDO>lambdaQuery().select(com.seeex.tech.web.organization.entity.EmployeeDO::getId,com.seeex.tech.web.organization.entity.EmployeeDO::getDisplayName).in(com.seeex.tech.web.organization.entity.EmployeeDO::getId,employeeIds)).forEach(row->people.put(row.getId(),new Option(row.getId(),row.getDisplayName())));
        var roleQuery=Wrappers.<com.seeex.tech.web.rbac.entity.RoleDO>lambdaQuery().select(com.seeex.tech.web.rbac.entity.RoleDO::getId,com.seeex.tech.web.rbac.entity.RoleDO::getName).isNull(com.seeex.tech.web.rbac.entity.RoleDO::getDeletedAt).eq(com.seeex.tech.web.rbac.entity.RoleDO::getStatus,"ACTIVE").like(!keyword.isEmpty(),com.seeex.tech.web.rbac.entity.RoleDO::getName,keyword).orderByAsc(com.seeex.tech.web.rbac.entity.RoleDO::getId);
        Map<Long,Option> roleOptions=new LinkedHashMap<>();
        roles.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,50,false),roleQuery).getRecords().forEach(row->roleOptions.put(row.getId(),new Option(row.getId(),row.getName())));
        if(!roleIds.isEmpty()) roles.selectList(Wrappers.<com.seeex.tech.web.rbac.entity.RoleDO>lambdaQuery().select(com.seeex.tech.web.rbac.entity.RoleDO::getId,com.seeex.tech.web.rbac.entity.RoleDO::getName).in(com.seeex.tech.web.rbac.entity.RoleDO::getId,roleIds)).forEach(row->roleOptions.put(row.getId(),new Option(row.getId(),row.getName())));
        return Map.of("employees",people.values(),"roles",roleOptions.values(),
            "departments",departments.selectList(Wrappers.<com.seeex.tech.web.organization.entity.DepartmentDO>lambdaQuery().select(com.seeex.tech.web.organization.entity.DepartmentDO::getId,com.seeex.tech.web.organization.entity.DepartmentDO::getName,com.seeex.tech.web.organization.entity.DepartmentDO::getParentId).isNull(com.seeex.tech.web.organization.entity.DepartmentDO::getDeletedAt).eq(com.seeex.tech.web.organization.entity.DepartmentDO::getStatus,"ACTIVE")).stream().map(row->new DepartmentOption(row.getId(),row.getName(),row.getParentId())).toList(),
            "resources",resources.selectList(Wrappers.<com.seeex.tech.web.rbac.entity.ResourceDO>lambdaQuery().select(com.seeex.tech.web.rbac.entity.ResourceDO::getId,com.seeex.tech.web.rbac.entity.ResourceDO::getName,com.seeex.tech.web.rbac.entity.ResourceDO::getParentId,com.seeex.tech.web.rbac.entity.ResourceDO::getPermissionCode,com.seeex.tech.web.rbac.entity.ResourceDO::getEntryType).eq(com.seeex.tech.web.rbac.entity.ResourceDO::getApplicationCode,app).isNull(com.seeex.tech.web.rbac.entity.ResourceDO::getDeletedAt).eq(com.seeex.tech.web.rbac.entity.ResourceDO::getStatus,"ACTIVE")));
    }
    private Set<Long> selected(List<Long> ids) { if(ids!=null && (ids.size()>1000 || ids.stream().anyMatch(Objects::isNull))) invalid("已选项无效或超过1000项"); return ids==null?new HashSet<>():new HashSet<>(ids); }
    /** 字典关键字与当前表单已选项；仅返回ID和名称。 */
    public record OptionQuery(String keyword,List<Long> employeeIds,List<Long> roleIds) { }
    private void validEmployees(Set<Long> ids) {
        if(ids.stream().anyMatch(Objects::isNull) || !ids.isEmpty() && employees.selectCount(Wrappers.<com.seeex.tech.web.organization.entity.EmployeeDO>lambdaQuery().in(com.seeex.tech.web.organization.entity.EmployeeDO::getId,ids).isNull(com.seeex.tech.web.organization.entity.EmployeeDO::getDeletedAt).eq(com.seeex.tech.web.organization.entity.EmployeeDO::getStatus,"ACTIVE"))!=ids.size()) invalid("员工不存在或已停用");
    }
    private void employee(Long id) { var row = id == null ? null : employees.selectById(id); if (row == null || row.getDeletedAt() != null || !"ACTIVE".equals(row.getStatus())) { invalid("员工不存在或已停用"); } }
    private void invalid(String message) { throw new BizException(ErrorCode.VALIDATION_FAILED, message); }
    /** 名称选择项。 */
    public record Option(Long id, String name) { }
    /** 部门树选择项。 */
    public record DepartmentOption(Long id, String name, Long parentId) { }
    /** 策略字段；自定义范围为实际人员、部门集合。 */
    public record PolicyCommand(String name, String subjectType, Long subjectId, Long resourceId, String effect, String scopeType, Set<Long> departmentIds, Set<Long> employeeIds, String status,Long expectedVersion) { }
}
