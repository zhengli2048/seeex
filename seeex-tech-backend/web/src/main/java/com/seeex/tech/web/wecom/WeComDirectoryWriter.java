package com.seeex.tech.web.wecom;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.organization.entity.*;
import com.seeex.tech.web.organization.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 单独受代理的原子写服务；此类禁止调用企业微信HTTP接口。 */
@Service @RequiredArgsConstructor
public class WeComDirectoryWriter {
    private final DepartmentMapper departments;
    private final EmployeeMapper employees;
    private final EmployeeDepartmentMapper memberships;
    private final WeComDirectoryService directory;
    private final SyncRunStateService runs;

    /** 两遍建立树在一个短事务完成，任何父级/版本冲突回滚整棵树。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void departments(List<WeComClient.Department> sources,WriteContext context) {
        fence(context);
        Map<Long,WeComClient.Department> byExternal=new TreeMap<>();
        if(sources==null || sources.isEmpty()) invalid("部门快照为空");
        for(var source:sources) {
            if(source==null || source.id()<=0 || source.name()==null || source.name().isBlank() || source.name().length()>128 || byExternal.put(source.id(),source)!=null) invalid("部门快照不完整或重复");
        }
        var all=departments.selectList(Wrappers.<DepartmentDO>lambdaQuery().in(DepartmentDO::getCode,byExternal.keySet().stream().map(id->"wecom-"+id).toList()));
        Map<String,DepartmentDO> locals=new HashMap<>(); all.forEach(row->locals.put(row.getCode(),row));
        // 快照必须闭合。根节点parentid=0；可见范围缺父级时不错误提升为根。
        for(var source:byExternal.values()) {
            Set<Long> visited=new HashSet<>(); var cursor=source;
            while(cursor.parentid()!=0) {
                if(!visited.add(cursor.id()) || !byExternal.containsKey(cursor.parentid())) invalid("部门父级缺失或存在循环");
                cursor=byExternal.get(cursor.parentid());
            }
            var local=locals.get("wecom-"+source.id());
            if(local!=null && local.getDeletedAt()!=null) invalid("同步部门身份已删除，需人工处理");
        }
        for(var source:byExternal.values()) {
            String code="wecom-"+source.id(); var row=locals.get(code);
            if(row==null) {
                row=new DepartmentDO(); row.setCode(code); row.setName(source.name()); row.setStatus("ACTIVE"); row.setSyncVersion(0L); departments.insert(row); locals.put(code,row);
            }
        }
        for(var source:byExternal.values()) {
            var row=locals.get("wecom-"+source.id());
            if(newer(row.getLastSyncedAt(),context.observedAt()) || context.eventAt()!=null && newer(row.getLastEventAt(),context.eventAt())) continue;
            Long expected=version(row.getSyncVersion()); row.setName(source.name()); row.setStatus("ACTIVE"); row.setParentId(source.parentid()==0?null:locals.get("wecom-"+source.parentid()).getId()); row.setLastSyncedAt(context.observedAt()); if(context.eventAt()!=null) row.setLastEventAt(context.eventAt()); row.setSyncVersion(expected+1);
            int count=departments.update(row,Wrappers.<DepartmentDO>lambdaQuery().eq(DepartmentDO::getId,row.getId()).eq(DepartmentDO::getSyncVersion,expected).isNull(DepartmentDO::getDeletedAt));
            if(count!=1) conflict();
        }
        // 旧快照跳过较新停用父节点后，也不能让其余成员形成不可用的父子链。
        Map<Long,DepartmentDO> byId=new HashMap<>(); locals.values().forEach(row->byId.put(row.getId(),row));
        for(var row:locals.values()) if("ACTIVE".equals(row.getStatus()) && row.getParentId()!=null) {
            var parent=byId.get(row.getParentId());
            if(parent==null || !"ACTIVE".equals(parent.getStatus())) conflict();
        }
        runs.committed(context.lease(),"DEPARTMENT_TREE","tree",false);
    }

    /** 资料、主部门、所有关系、职级、同步版本与成功结果同事务提交。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void employee(WeComClient.User source,WriteContext context) {
        fence(context);
        String no=source==null || source.userid()==null?"":"wc_"+source.userid();
        if(no.length()<=3 || no.length()>32 || source.name()==null || source.name().isBlank() || source.name().length()>128 || source.department()==null || source.department().isEmpty() || source.department().stream().anyMatch(Objects::isNull) || new HashSet<>(source.department()).size()!=source.department().size()) invalid("成员标识、姓名或部门关系无效");
        if(source.mobile()!=null && source.mobile().length()>32 || source.email()!=null && source.email().length()>256) invalid("成员联系方式超过本地字段限制");
        var row=employees.selectOne(Wrappers.<EmployeeDO>lambdaQuery().eq(EmployeeDO::getEmployeeNo,no));
        if(row!=null && row.getDeletedAt()!=null) invalid("同步员工身份已删除");
        if(row!=null && (newer(row.getLastSyncedAt(),context.observedAt()) || context.eventAt()!=null && newer(row.getLastEventAt(),context.eventAt()))) { runs.committed(context.lease(),"EMPLOYEE",source.userid(),true); return; }
        var targets=departments.selectList(Wrappers.<DepartmentDO>lambdaQuery().in(DepartmentDO::getCode,source.department().stream().map(id->"wecom-"+id).toList()).isNull(DepartmentDO::getDeletedAt).eq(DepartmentDO::getStatus,"ACTIVE"));
        Map<String,DepartmentDO> byCode=new HashMap<>(); targets.forEach(department->byCode.put(department.getCode(),department));
        if(byCode.size()!=source.department().size()) invalid("成员有尚未同步或已停用的部门");
        // 所有目标预校验通过后才能开始变更；CAS先锁定员工行，避免并发关系替换。
        Long mapped=directory.mappedGrade(source.position()); boolean fresh=row==null;
        if(fresh) { row=new EmployeeDO(); row.setEmployeeNo(no); row.setSyncVersion(0L); }
        Long preserved=row.getJobGradeId(), expected=version(row.getSyncVersion());
        var old=fresh?List.<EmployeeDepartmentDO>of():memberships.selectList(Wrappers.<EmployeeDepartmentDO>lambdaQuery().eq(EmployeeDepartmentDO::getEmployeeId,row.getId()).isNull(EmployeeDepartmentDO::getDeletedAt));
        Map<Long,Long> previous=new HashMap<>(); old.forEach(member->previous.put(member.getDepartmentId(),member.getJobGradeId()));
        Long primary=byCode.get("wecom-"+source.department().getFirst()).getId();
        row.setDisplayName(source.name()); row.setSource("WECOM"); row.setStatus(source.status()!=null && source.status()==2?"DISABLED":"ACTIVE"); row.setPrimaryDepartmentId(primary); row.setJobGradeId(mapped!=null?mapped:previous.containsKey(primary)?previous.get(primary):preserved); row.setInternalMobile(source.mobile()); row.setInternalEmail(source.email()); row.setLastSyncedAt(context.observedAt()); if(context.eventAt()!=null) row.setLastEventAt(context.eventAt()); row.setSyncVersion(expected+1);
        if(fresh) employees.insert(row);
        else if(employees.update(row,Wrappers.<EmployeeDO>lambdaQuery().eq(EmployeeDO::getId,row.getId()).eq(EmployeeDO::getSyncVersion,expected).isNull(EmployeeDO::getDeletedAt))!=1) conflict();
        memberships.delete(Wrappers.<EmployeeDepartmentDO>lambdaQuery().eq(EmployeeDepartmentDO::getEmployeeId,row.getId()));
        for(int index=0;index<source.department().size();index++) {
            Long local=byCode.get("wecom-"+source.department().get(index)).getId();
            var member=new EmployeeDepartmentDO(); member.setEmployeeId(row.getId()); member.setDepartmentId(local); member.setIsPrimary(index==0); member.setJobGradeId(mapped!=null?mapped:previous.containsKey(local)?previous.get(local):preserved); memberships.insert(member);
        }
        runs.committed(context.lease(),"EMPLOYEE",source.userid(),false);
    }
    /** 删除事件仅软停用，以版本与时间戳拒绝旧快照覆盖。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void deactivate(String kind,String externalId,LocalDateTime sourceTime) {
        if("EMPLOYEE".equals(kind)) {
            var row=employees.selectOne(Wrappers.<EmployeeDO>lambdaQuery().eq(EmployeeDO::getEmployeeNo,"wc_"+externalId));
            if(row==null || row.getDeletedAt()!=null || newer(row.getLastEventAt(),sourceTime)) return;
            Long expected=version(row.getSyncVersion());
            if(employees.update(null,Wrappers.<EmployeeDO>lambdaUpdate().eq(EmployeeDO::getId,row.getId()).eq(EmployeeDO::getSyncVersion,expected).set(EmployeeDO::getStatus,"LEFT").set(EmployeeDO::getLastSyncedAt,LocalDateTime.now()).set(EmployeeDO::getLastEventAt,sourceTime).set(EmployeeDO::getSyncVersion,expected+1))!=1) conflict();
        } else {
            var row=departments.selectOne(Wrappers.<DepartmentDO>lambdaQuery().eq(DepartmentDO::getCode,"wecom-"+externalId));
            if(row==null || row.getDeletedAt()!=null || newer(row.getLastEventAt(),sourceTime)) return;
            Long expected=version(row.getSyncVersion());
            if(departments.update(null,Wrappers.<DepartmentDO>lambdaUpdate().eq(DepartmentDO::getId,row.getId()).eq(DepartmentDO::getSyncVersion,expected).set(DepartmentDO::getStatus,"INACTIVE").set(DepartmentDO::getLastSyncedAt,LocalDateTime.now()).set(DepartmentDO::getLastEventAt,sourceTime).set(DepartmentDO::getSyncVersion,expected+1))!=1) conflict();
        }
    }
    private void fence(WriteContext context) { if(context==null || context.observedAt()==null) invalid("同步上下文缺失"); if(context.lease()!=null) runs.fence(context.lease()); }
    private boolean newer(LocalDateTime value,LocalDateTime snapshot) { return value!=null && value.isAfter(snapshot); }
    private Long version(Long value) { return value==null?0L:value; }
    private void invalid(String message) { throw new BizException(ErrorCode.VALIDATION_FAILED,message); }
    private void conflict() { throw new BizException(ErrorCode.CONFLICT,"同步对象已被其他任务更新，请重试"); }
    /** observedAt是远端读取开始时刻，旧快照不能覆盖较新的同步提交。 */
    public record WriteContext(SyncRunStateService.Lease lease,LocalDateTime observedAt,LocalDateTime eventAt) {
        /** 全量快照不覆盖事件源序号。 */
        public WriteContext(SyncRunStateService.Lease lease,LocalDateTime observedAt) { this(lease,observedAt,null); }
    }
}
