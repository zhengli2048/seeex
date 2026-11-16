package com.seeex.tech.web.rbac;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.governance.ConfigurationRevisionService;
import com.seeex.tech.web.organization.mapper.*;
import com.seeex.tech.web.rbac.entity.*;
import com.seeex.tech.web.rbac.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** 单资源范围写入与集合版本同事务，删除不重置版本。 */
@Service @RequiredArgsConstructor
public class RoleDataScopeService {
    private final RoleDataScopeMapper mapper;
    private final RoleMapper roles;
    private final ResourceMapper resources;
    private final EmployeeMapper employees;
    private final DepartmentMapper departments;
    private final ConfigurationRevisionService revisions;
    private final ObjectMapper json;
    /** 返回范围及读取时的集合版本。 */
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ScopeView list(long id) { role(id); long version=revisions.current(key(id)); return new ScopeView(mapper.selectList(Wrappers.<RoleDataScopeDO>lambdaQuery().eq(RoleDataScopeDO::getRoleId,id).isNull(RoleDataScopeDO::getDeletedAt)),version); }
    /** 先CAS锁定集合，再替换目标资源，保留其他资源。 */
    @Transactional
    public void save(long id,ScopeCommand command) {
        role(id);
        if(command.resourceCode()==null || !List.of("SELF","CUSTOM","DEPARTMENT","DEPARTMENT_AND_DESCENDANTS","ALL").contains(command.scopeType()==null?"":command.scopeType()) || resources.selectCount(Wrappers.<ResourceDO>lambdaQuery().eq(ResourceDO::getPermissionCode,command.resourceCode()).isNull(ResourceDO::getDeletedAt).eq(ResourceDO::getStatus,"ACTIVE"))==0) invalid("资源或范围无效");
        Set<Long> ds=command.departmentIds()==null?Set.of():command.departmentIds(), es=command.employeeIds()==null?Set.of():command.employeeIds();
        if(ds.size()+es.size()>1000) invalid("自定义范围最多1000项");
        if(ds.stream().anyMatch(Objects::isNull) || !ds.isEmpty() && departments.selectCount(Wrappers.<com.seeex.tech.web.organization.entity.DepartmentDO>lambdaQuery().in(com.seeex.tech.web.organization.entity.DepartmentDO::getId,ds).isNull(com.seeex.tech.web.organization.entity.DepartmentDO::getDeletedAt).eq(com.seeex.tech.web.organization.entity.DepartmentDO::getStatus,"ACTIVE"))!=ds.size()) invalid("部门无效");
        if(es.stream().anyMatch(Objects::isNull) || !es.isEmpty() && employees.selectCount(Wrappers.<com.seeex.tech.web.organization.entity.EmployeeDO>lambdaQuery().in(com.seeex.tech.web.organization.entity.EmployeeDO::getId,es).isNull(com.seeex.tech.web.organization.entity.EmployeeDO::getDeletedAt).eq(com.seeex.tech.web.organization.entity.EmployeeDO::getStatus,"ACTIVE"))!=es.size()) invalid("员工无效");
        revisions.advance(key(id),command.expectedVersion());
        mapper.delete(Wrappers.<RoleDataScopeDO>lambdaQuery().eq(RoleDataScopeDO::getRoleId,id).eq(RoleDataScopeDO::getResourceCode,command.resourceCode()));
        var row=new RoleDataScopeDO(); row.setRoleId(id); row.setResourceCode(command.resourceCode()); row.setScopeType(command.scopeType());
        try { row.setScopeConfig(json.writeValueAsString(Map.of("departmentIds",ds,"employeeIds",es))); } catch(com.fasterxml.jackson.core.JsonProcessingException ex) { invalid("范围格式无效"); }
        mapper.insert(row);
    }
    /** 删除与版本推进原子提交，空配置也能拒绝旧请求。 */
    @Transactional
    public void delete(long id,String resourceCode,Long expectedVersion) { role(id); if(resourceCode==null || resourceCode.isBlank()) invalid("资源必填"); revisions.advance(key(id),expectedVersion); mapper.delete(Wrappers.<RoleDataScopeDO>lambdaQuery().eq(RoleDataScopeDO::getRoleId,id).eq(RoleDataScopeDO::getResourceCode,resourceCode)); }
    private String key(long id) { return "role-scope:"+id; }
    private void role(long id) { var row=roles.selectById(id); if(row==null || row.getDeletedAt()!=null) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND,"角色不存在"); }
    private void invalid(String message) { throw new BizException(ErrorCode.VALIDATION_FAILED,message); }
    /** 集合快照。 */
    public record ScopeView(List<RoleDataScopeDO> items,long version) { }
    /** 单资源范围更新请求。 */
    public record ScopeCommand(String resourceCode,String scopeType,Set<Long> departmentIds,Set<Long> employeeIds,Long expectedVersion) { }
}
