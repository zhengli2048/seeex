package com.seeex.tech.web.organization;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.organization.entity.DepartmentDO;
import com.seeex.tech.web.organization.entity.EmployeeDO;
import com.seeex.tech.web.organization.entity.EmployeeDepartmentDO;
import com.seeex.tech.web.organization.entity.JobGradeDO;
import com.seeex.tech.web.organization.mapper.DepartmentMapper;
import com.seeex.tech.web.organization.mapper.EmployeeDepartmentMapper;
import com.seeex.tech.web.organization.mapper.EmployeeMapper;
import com.seeex.tech.web.organization.mapper.JobGradeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/** 员工、部门和职级的持久化管理服务。 */
@Service
@RequiredArgsConstructor
public class OrganizationManagementService {

    private static final String ACTIVE = "ACTIVE";

    /** 员工业务编号前缀。 */
    private static final String PREFIX_EMPLOYEE = "EMP";

    /** 部门业务编号前缀。 */
    private static final String PREFIX_DEPARTMENT = "DEPT";

    /** 职级业务编号前缀。 */
    private static final String PREFIX_JOB_GRADE = "JG";

    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final JobGradeMapper jobGradeMapper;
    private final EmployeeDepartmentMapper employeeDepartmentMapper;
    private final SequenceGenerator sequenceGenerator;
    @org.springframework.beans.factory.annotation.Autowired private OrganizationGrantGuard grantGuard;
    @org.springframework.beans.factory.annotation.Autowired private com.seeex.tech.web.rbac.mapper.RoleMapper roleMapper;
    @org.springframework.beans.factory.annotation.Autowired private com.seeex.tech.web.rbac.PermissionService permissionService;

    /** 查询员工分页。 */
    public PageResult<EmployeeItem> employees(int page, int pageSize, String keyword, String status) {
        Page<EmployeeDO> result = employeeMapper.selectPage(new Page<>(page, pageSize), employeeQuery(keyword, status));
        return new PageResult<>(employeeItems(result.getRecords()), result.getTotal());
    }

    /** 按组织和职级筛选员工。 */
    public PageResult<EmployeeItem> employees(EmployeeQuery filter) {
        LambdaQueryWrapper<EmployeeDO> query = employeeQuery(filter.keyword(), filter.status());
        if (filter.departmentId() != null) {
            List<Long> members = employeeDepartmentMapper.selectList(new LambdaQueryWrapper<EmployeeDepartmentDO>()
                    .eq(EmployeeDepartmentDO::getDepartmentId, filter.departmentId()).isNull(EmployeeDepartmentDO::getDeletedAt))
                    .stream().map(EmployeeDepartmentDO::getEmployeeId).toList();
            query.and(w -> { w.eq(EmployeeDO::getPrimaryDepartmentId, filter.departmentId()); if (!members.isEmpty()) { w.or().in(EmployeeDO::getId, members); } });
        }
        if (filter.jobGradeId() != null) {
            List<Long> members = employeeDepartmentMapper.selectList(new LambdaQueryWrapper<EmployeeDepartmentDO>()
                    .eq(EmployeeDepartmentDO::getJobGradeId, filter.jobGradeId()).isNull(EmployeeDepartmentDO::getDeletedAt))
                    .stream().map(EmployeeDepartmentDO::getEmployeeId).toList();
            query.and(w -> { w.eq(EmployeeDO::getJobGradeId, filter.jobGradeId()); if (!members.isEmpty()) { w.or().in(EmployeeDO::getId, members); } });
        }
        Page<EmployeeDO> result = employeeMapper.selectPage(new Page<>(Math.max(1, filter.page()), Math.min(100, Math.max(1, filter.pageSize() == 0 ? 20 : filter.pageSize()))), query);
        return new PageResult<>(employeeItems(result.getRecords()), result.getTotal());
    }

    /** 查询单个员工详情。 */
    public EmployeeItem employee(long id) { return employeeItem(requireEntity(employeeMapper.selectById(id), "employee")); }

    /** 查询部门分页。 */
    public PageResult<DepartmentItem> departments(int page, int pageSize, String keyword, String status) {
        Page<DepartmentDO> result = departmentMapper.selectPage(new Page<>(page, pageSize), departmentQuery(keyword, status));
        return new PageResult<>(departmentItems(result.getRecords()), result.getTotal());
    }

    /** 查询单个部门详情。 */
    public DepartmentItem department(long id) { return departmentItem(requireEntity(departmentMapper.selectById(id), "department")); }

    /** 查询职级分页。 */
    public PageResult<JobGradeItem> jobGrades(int page, int pageSize, String keyword, String status) {
        Page<JobGradeDO> result = jobGradeMapper.selectPage(new Page<>(page, pageSize), jobGradeQuery(keyword, status));
        return new PageResult<>(result.getRecords().stream().map(this::jobGradeItem).toList(), result.getTotal());
    }

    /** 查询单个职级详情。 */
    public JobGradeItem jobGrade(long id) { return jobGradeItem(requireEntity(jobGradeMapper.selectById(id), "job grade")); }

    /** 创建员工。 */
    @Transactional
    public EmployeeItem createEmployee(EmployeeCommand command) {
        String employeeNo = command.employeeNo();
        if (employeeNo == null || employeeNo.isBlank()) {
            employeeNo = nextCode(PREFIX_EMPLOYEE, employeeMapper.selectList(new LambdaQueryWrapper<EmployeeDO>()
                    .select(EmployeeDO::getEmployeeNo)).stream().map(EmployeeDO::getEmployeeNo).toList());
        }
        requireText(employeeNo, "employeeNo");
        requireText(command.displayName(), "displayName");
        ensureUnique(employeeMapper.selectCount(new LambdaQueryWrapper<EmployeeDO>()
                .eq(EmployeeDO::getEmployeeNo, employeeNo).isNull(EmployeeDO::getDeletedAt)), "employeeNo");
        var createAuth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(createAuth!=null) grantGuard.employee(createAuth,null,command);
        EmployeeDO entity = new EmployeeDO();
        entity.setEmployeeNo(employeeNo);
        entity.setDisplayName(command.displayName());
        entity.setStatus(normalizeStatus(command.status()));
        entity.setPrimaryDepartmentId(command.primaryDepartmentId());
        entity.setJobGradeId(command.jobGradeId());
        entity.setSource("LOCAL");
        applyEmployee(entity, command);
        employeeMapper.insert(entity);
        saveMemberships(entity.getId(), normalizedMemberships(command));
        return employeeItem(entity);
    }

    /** 更新员工。 */
    @Transactional
    public EmployeeItem updateEmployee(long id, EmployeeCommand command) {
        EmployeeDO entity = requireEntity(employeeMapper.selectById(id), "employee");
        var editAuth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(editAuth!=null) grantGuard.employee(editAuth,id,command);
        long syncVersion=entity.getSyncVersion()==null?0:entity.getSyncVersion();
        if (command.displayName() != null) {
            requireText(command.displayName(), "displayName");
            entity.setDisplayName(command.displayName());
        }
        if (command.status() != null) {
            entity.setStatus(normalizeStatus(command.status()));
        }
        applyEmployee(entity, command);
        entity.setSyncVersion(syncVersion+1); entity.setLastSyncedAt(LocalDateTime.now());
        if(employeeMapper.update(entity,new LambdaQueryWrapper<EmployeeDO>().eq(EmployeeDO::getId,id).eq(EmployeeDO::getSyncVersion,syncVersion).isNull(EmployeeDO::getDeletedAt))!=1) throw new BizException(ErrorCode.CONFLICT,"员工已被同步或其他管理员更新，请重新载入");
        saveMemberships(id, normalizedMemberships(command));
        return employeeItem(entity);
    }

    /** 软删除员工。 */
    @Transactional
    public void deleteEmployees(List<Long> ids) {
        updateEach(ids, employeeMapper, EmployeeDO.class, entity -> entity.setDeletedAt(LocalDateTime.now()));
        employeeDepartmentMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<EmployeeDepartmentDO>()
                .in(EmployeeDepartmentDO::getEmployeeId, ids).isNull(EmployeeDepartmentDO::getDeletedAt).set(EmployeeDepartmentDO::getDeletedAt, LocalDateTime.now()));
    }

    /** 创建部门。 */
    @Transactional
    public DepartmentItem createDepartment(DepartmentCommand command) {
        String code = command.code();
        if (code == null || code.isBlank()) {
            code = nextCode(PREFIX_DEPARTMENT, departmentMapper.selectList(new LambdaQueryWrapper<DepartmentDO>()
                    .select(DepartmentDO::getCode)).stream().map(DepartmentDO::getCode).toList());
        }
        requireText(code, "code");
        requireText(command.name(), "name");
        ensureUnique(departmentMapper.selectCount(new LambdaQueryWrapper<DepartmentDO>()
                .eq(DepartmentDO::getCode, code).isNull(DepartmentDO::getDeletedAt)), "code");
        DepartmentDO entity = new DepartmentDO();
        entity.setCode(code);
        entity.setName(command.name());
        validateParent(null, command.parentId());
        entity.setParentId(command.parentId());
        entity.setLeaderEmployeeId(command.leaderEmployeeId());
        entity.setStatus(normalizeStatus(command.status()));
        departmentMapper.insert(entity);
        return departmentItem(entity);
    }

    /** 更新部门。 */
    @Transactional
    public DepartmentItem updateDepartment(long id, DepartmentCommand command) {
        DepartmentDO entity = requireEntity(departmentMapper.selectById(id), "department");
        long syncVersion=entity.getSyncVersion()==null?0:entity.getSyncVersion();
        if (command.name() != null) {
            requireText(command.name(), "name");
            entity.setName(command.name());
        }
        validateParent(id, command.parentId());
        entity.setParentId(command.parentId());
        entity.setLeaderEmployeeId(command.leaderEmployeeId());
        if (command.status() != null) {
            entity.setStatus(normalizeStatus(command.status()));
        }
        entity.setSyncVersion(syncVersion+1); entity.setLastSyncedAt(LocalDateTime.now());
        if(departmentMapper.update(entity,new LambdaQueryWrapper<DepartmentDO>().eq(DepartmentDO::getId,id).eq(DepartmentDO::getSyncVersion,syncVersion).isNull(DepartmentDO::getDeletedAt))!=1) throw new BizException(ErrorCode.CONFLICT,"部门已被同步或其他管理员更新，请重新载入");
        return departmentItem(entity);
    }

    /** 软删除部门。 */
    @Transactional
    public void deleteDepartments(List<Long> ids) {
        if (ids == null || ids.isEmpty()) { throw new BizException(ErrorCode.VALIDATION_FAILED, "请选择部门"); }
        if (departmentMapper.selectCount(new LambdaQueryWrapper<DepartmentDO>().in(DepartmentDO::getParentId, ids).notIn(DepartmentDO::getId, ids).isNull(DepartmentDO::getDeletedAt)) > 0
                || employeeMapper.selectCount(new LambdaQueryWrapper<EmployeeDO>().in(EmployeeDO::getPrimaryDepartmentId, ids).isNull(EmployeeDO::getDeletedAt)) > 0
                || employeeDepartmentMapper.selectCount(new LambdaQueryWrapper<EmployeeDepartmentDO>().in(EmployeeDepartmentDO::getDepartmentId, ids).isNull(EmployeeDepartmentDO::getDeletedAt)) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "部门仍有关联员工或子部门");
        }
        updateEach(ids, departmentMapper, DepartmentDO.class, entity -> entity.setDeletedAt(LocalDateTime.now()));
    }

    /** 创建职级。 */
    @Transactional
    public JobGradeItem createJobGrade(JobGradeCommand command) {
        String code = command.code();
        if (code == null || code.isBlank()) {
            code = nextCode(PREFIX_JOB_GRADE, jobGradeMapper.selectList(new LambdaQueryWrapper<JobGradeDO>()
                    .select(JobGradeDO::getCode)).stream().map(JobGradeDO::getCode).toList());
        }
        requireText(code, "code");
        requireText(command.name(), "name");
        ensureUnique(jobGradeMapper.selectCount(new LambdaQueryWrapper<JobGradeDO>()
                .eq(JobGradeDO::getCode, code).isNull(JobGradeDO::getDeletedAt)), "code");
        validateDefaultRole(command.defaultRoleId());
        validateLevel(null, command.levelValue());
        JobGradeDO entity = new JobGradeDO();
        entity.setCode(code);
        entity.setName(command.name());
        entity.setLevelValue(command.levelValue());
        entity.setDefaultRoleId(command.defaultRoleId());
        entity.setStatus(normalizeStatus(command.status()));
        jobGradeMapper.insert(entity);
        return jobGradeItem(entity);
    }

    /** 更新职级。 */
    @Transactional
    public JobGradeItem updateJobGrade(long id, JobGradeCommand command) {
        JobGradeDO entity = requireEntity(jobGradeMapper.selectById(id), "job grade");
        if (command.name() != null) {
            requireText(command.name(), "name");
            entity.setName(command.name());
        }
        validateDefaultRole(command.defaultRoleId());
        validateLevel(id, command.levelValue());
        if (command.levelValue() != null) {
            entity.setLevelValue(command.levelValue());
        }
        entity.setDefaultRoleId(command.defaultRoleId());
        if (command.status() != null) {
            entity.setStatus(normalizeStatus(command.status()));
        }
        jobGradeMapper.updateById(entity);
        return jobGradeItem(entity);
    }

    /** 软删除职级。 */
    @Transactional
    public void deleteJobGrades(List<Long> ids) {
        if (ids == null || ids.isEmpty()) { throw new BizException(ErrorCode.VALIDATION_FAILED, "请选择职级"); }
        if (employeeMapper.selectCount(new LambdaQueryWrapper<EmployeeDO>().in(EmployeeDO::getJobGradeId, ids).isNull(EmployeeDO::getDeletedAt)) > 0
                || employeeDepartmentMapper.selectCount(new LambdaQueryWrapper<EmployeeDepartmentDO>().in(EmployeeDepartmentDO::getJobGradeId, ids).isNull(EmployeeDepartmentDO::getDeletedAt)) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "职级仍有关联员工");
        }
        updateEach(ids, jobGradeMapper, JobGradeDO.class, entity -> entity.setDeletedAt(LocalDateTime.now()));
    }

    private void validateDefaultRole(Long id) {
        if(id==null) return;
        var role=roleMapper.selectById(id);
        if(role==null || role.getDeletedAt()!=null || !ACTIVE.equals(role.getStatus())) throw new BizException(ErrorCode.VALIDATION_FAILED,"默认角色不存在或已停用");
    }

    private void validateLevel(Long id, Integer level) {
        if (level != null && (level < 0 || level > 9999)) { throw new BizException(ErrorCode.VALIDATION_FAILED, "等级值须为 0–9999"); }
        if (level != null && jobGradeMapper.selectCount(new LambdaQueryWrapper<JobGradeDO>().eq(JobGradeDO::getLevelValue, level).ne(id != null, JobGradeDO::getId, id).isNull(JobGradeDO::getDeletedAt)) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "等级值已存在");
        }
    }

    private void validateParent(Long id, Long parent) {
        java.util.Set<Long> visited = new java.util.HashSet<>();
        while (parent != null) {
            if (java.util.Objects.equals(id, parent) || !visited.add(parent)) { throw new BizException(ErrorCode.VALIDATION_FAILED, "上级部门不能为自身或后代"); }
            DepartmentDO row = requireEntity(departmentMapper.selectById(parent), "上级部门");
            if (row.getDeletedAt() != null) { throw new BizException(ErrorCode.VALIDATION_FAILED, "上级部门已删除"); }
            parent = row.getParentId();
        }
    }

    private List<DepartmentMembership> normalizedMemberships(EmployeeCommand command) {
        List<DepartmentMembership> rows = command.memberships();
        if (rows == null) { rows = command.primaryDepartmentId() == null ? List.of() : List.of(new DepartmentMembership(command.primaryDepartmentId(), command.jobGradeId(), true)); }
        if (!rows.isEmpty() && rows.stream().filter(row -> Boolean.TRUE.equals(row.primary())).count() != 1) { throw new BizException(ErrorCode.VALIDATION_FAILED, "必须且只能有一个主部门"); }
        java.util.Set<Long> departments = new java.util.HashSet<>();
        for (DepartmentMembership row : rows) {
            if (row.departmentId() == null || !departments.add(row.departmentId())) { throw new BizException(ErrorCode.VALIDATION_FAILED, "所属部门不可为空或重复"); }
            DepartmentDO department = requireEntity(departmentMapper.selectById(row.departmentId()), "部门");
            if (department.getDeletedAt() != null || !ACTIVE.equals(department.getStatus())) { throw new BizException(ErrorCode.VALIDATION_FAILED, "部门不可用"); }
            if (row.jobGradeId() != null) {
                JobGradeDO grade = requireEntity(jobGradeMapper.selectById(row.jobGradeId()), "职级");
                if (grade.getDeletedAt() != null || !ACTIVE.equals(grade.getStatus())) { throw new BizException(ErrorCode.VALIDATION_FAILED, "职级不可用"); }
            }
        }
        return rows;
    }

    private void applyEmployee(EmployeeDO entity, EmployeeCommand command) {
        if (command.internalMobile() != null && !command.internalMobile().isBlank() && !command.internalMobile().matches("1[0-9]{10}")) { throw new BizException(ErrorCode.VALIDATION_FAILED, "手机号格式不合法"); }
        if (command.internalEmail() != null && !command.internalEmail().isBlank() && !command.internalEmail().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) { throw new BizException(ErrorCode.VALIDATION_FAILED, "邮箱格式不合法"); }
        if (!List.of("ACTIVE", "DISABLED", "LEFT", "LOCKED").contains(normalizeStatus(command.status()))) { throw new BizException(ErrorCode.VALIDATION_FAILED, "员工状态不合法"); }
        List<DepartmentMembership> rows = normalizedMemberships(command);
        DepartmentMembership primary = rows.stream().filter(row -> Boolean.TRUE.equals(row.primary())).findFirst().orElse(null);
        entity.setPrimaryDepartmentId(primary == null ? null : primary.departmentId());
        entity.setJobGradeId(primary == null ? null : primary.jobGradeId());
        entity.setInternalMobile(command.internalMobile()); entity.setInternalEmail(command.internalEmail());
    }

    private void saveMemberships(Long employeeId, List<DepartmentMembership> memberships) {
        if (memberships == null) {
            return;
        }
        employeeDepartmentMapper.delete(new LambdaQueryWrapper<EmployeeDepartmentDO>()
                .eq(EmployeeDepartmentDO::getEmployeeId, employeeId));
        memberships.forEach(membership -> {
            EmployeeDepartmentDO row = new EmployeeDepartmentDO();
            row.setEmployeeId(employeeId);
            row.setDepartmentId(membership.departmentId());
            row.setJobGradeId(membership.jobGradeId());
            row.setIsPrimary(Boolean.TRUE.equals(membership.primary()));
            employeeDepartmentMapper.insert(row);
        });
    }

    private String nextCode(String prefix, List<String> existingCodes) {
        return sequenceGenerator.next(prefix, sequenceGenerator.maxSeq(prefix, existingCodes));
    }

    private com.seeex.tech.web.rbac.DataScopeService.DataScope currentScope(String resource) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        return permissionService.resolveDataScope(Long.parseLong(auth.getName()),resource);
    }

    private LambdaQueryWrapper<EmployeeDO> employeeQuery(String keyword, String status) {
        LambdaQueryWrapper<EmployeeDO> query = new LambdaQueryWrapper<>();
        query.isNull(EmployeeDO::getDeletedAt).eq(status != null && !status.isBlank(), EmployeeDO::getStatus, status);
        query.and(keyword != null && !keyword.isBlank(), wrapper -> wrapper.like(EmployeeDO::getEmployeeNo, keyword)
                .or().like(EmployeeDO::getDisplayName, keyword));
        var dataScope = currentScope("employees:view");
        if (dataScope != null && dataScope.type() != com.seeex.tech.web.rbac.DataScopeService.ScopeType.ALL) {
            java.util.Set<Long> visible = new java.util.HashSet<>(dataScope.employeeIds());
            if (!dataScope.departmentIds().isEmpty()) employeeDepartmentMapper.selectList(new LambdaQueryWrapper<EmployeeDepartmentDO>().in(EmployeeDepartmentDO::getDepartmentId,dataScope.departmentIds()).isNull(EmployeeDepartmentDO::getDeletedAt)).forEach(row -> visible.add(row.getEmployeeId()));
            query.in(EmployeeDO::getId, visible.isEmpty() ? java.util.Set.of(-1L) : visible);
        }
        return query.orderByDesc(EmployeeDO::getId);
    }

    private LambdaQueryWrapper<DepartmentDO> departmentQuery(String keyword, String status) {
        LambdaQueryWrapper<DepartmentDO> query = new LambdaQueryWrapper<>();
        query.isNull(DepartmentDO::getDeletedAt).eq(status != null && !status.isBlank(), DepartmentDO::getStatus, status);
        query.and(keyword != null && !keyword.isBlank(), wrapper -> wrapper.like(DepartmentDO::getCode, keyword)
                .or().like(DepartmentDO::getName, keyword));
        var dataScope = currentScope("departments:view");
        if (dataScope != null && dataScope.type() != com.seeex.tech.web.rbac.DataScopeService.ScopeType.ALL) {
            java.util.Set<Long> visible = new java.util.HashSet<>(dataScope.departmentIds());
            if (!dataScope.employeeIds().isEmpty()) employeeDepartmentMapper.selectList(new LambdaQueryWrapper<EmployeeDepartmentDO>().in(EmployeeDepartmentDO::getEmployeeId,dataScope.employeeIds()).isNull(EmployeeDepartmentDO::getDeletedAt)).forEach(row -> visible.add(row.getDepartmentId()));
            query.in(DepartmentDO::getId, visible.isEmpty() ? java.util.Set.of(-1L) : visible);
        }
        return query.orderByDesc(DepartmentDO::getId);
    }

    private LambdaQueryWrapper<JobGradeDO> jobGradeQuery(String keyword, String status) {
        LambdaQueryWrapper<JobGradeDO> query = new LambdaQueryWrapper<>();
        query.isNull(JobGradeDO::getDeletedAt).eq(status != null && !status.isBlank(), JobGradeDO::getStatus, status);
        query.and(keyword != null && !keyword.isBlank(), wrapper -> wrapper.like(JobGradeDO::getCode, keyword)
                .or().like(JobGradeDO::getName, keyword));
        return query.orderByDesc(JobGradeDO::getId);
    }

    private <T> void updateEach(List<Long> ids, com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                Class<T> type, Consumer<T> consumer) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "ids must not be empty");
        }
        ids.forEach(id -> {
            T entity = mapper.selectById(id);
            if (entity == null) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, type.getSimpleName() + " not found");
            }
            consumer.accept(entity);
            mapper.updateById(entity);
        });
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, field + " must not be blank");
        }
    }

    private static void ensureUnique(long count, String field) {
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, field + " already exists");
        }
    }

    private static String normalizeStatus(String status) {
        return status == null || status.isBlank() ? ACTIVE : status.toUpperCase();
    }

    private static <T> T requireEntity(T entity, String name) {
        boolean deleted = entity instanceof EmployeeDO employee && employee.getDeletedAt() != null
                || entity instanceof DepartmentDO department && department.getDeletedAt() != null
                || entity instanceof JobGradeDO grade && grade.getDeletedAt() != null;
        if (entity == null || deleted) { throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, name + " not found"); }
        return entity;
    }

    private EmployeeItem employeeItem(EmployeeDO entity) { return employeeItems(List.of(entity)).getFirst(); }
    private List<EmployeeItem> employeeItems(List<EmployeeDO> rows) {
        if(rows.isEmpty()) return List.of();
        var ids=rows.stream().map(EmployeeDO::getId).toList();
        java.util.Map<Long,List<DepartmentMembership>> byEmployee=new java.util.HashMap<>();
        employeeDepartmentMapper.selectList(new LambdaQueryWrapper<EmployeeDepartmentDO>().in(EmployeeDepartmentDO::getEmployeeId,ids).isNull(EmployeeDepartmentDO::getDeletedAt)).forEach(row->byEmployee.computeIfAbsent(row.getEmployeeId(),ignored->new java.util.ArrayList<>()).add(new DepartmentMembership(row.getDepartmentId(),row.getJobGradeId(),row.getIsPrimary())));
        return rows.stream().map(entity->new EmployeeItem(entity.getId(),entity.getEmployeeNo(),entity.getDisplayName(),entity.getStatus(),entity.getPrimaryDepartmentId(),entity.getJobGradeId(),entity.getInternalMobile(),entity.getInternalEmail(),entity.getSource(),byEmployee.getOrDefault(entity.getId(),List.of()),entity.getUpdatedAt())).toList();
    }
    private DepartmentItem departmentItem(DepartmentDO entity) { return departmentItems(List.of(entity)).getFirst(); }
    private List<DepartmentItem> departmentItems(List<DepartmentDO> rows) {
        if(rows.isEmpty()) return List.of();
        var ids=rows.stream().map(DepartmentDO::getId).collect(java.util.stream.Collectors.toSet());
        var parents=rows.stream().map(DepartmentDO::getParentId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long,String> names=new java.util.HashMap<>();
        if(!parents.isEmpty()) departmentMapper.selectList(new LambdaQueryWrapper<DepartmentDO>().select(DepartmentDO::getId,DepartmentDO::getName).in(DepartmentDO::getId,parents).isNull(DepartmentDO::getDeletedAt)).forEach(row->names.put(row.getId(),row.getName()));
        java.util.Map<Long,java.util.Set<Long>> people=new java.util.HashMap<>();
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean canCount=auth==null || permissionService.hasPermission(Long.parseLong(auth.getName()),"employees:view");
        if(canCount) {
            var members=employeeDepartmentMapper.selectList(new LambdaQueryWrapper<EmployeeDepartmentDO>().in(EmployeeDepartmentDO::getDepartmentId,ids).isNull(EmployeeDepartmentDO::getDeletedAt));
            var candidateIds=members.stream().map(EmployeeDepartmentDO::getEmployeeId).collect(java.util.stream.Collectors.toSet());
            var query=employeeQuery(null,"ACTIVE").select(EmployeeDO::getId,EmployeeDO::getPrimaryDepartmentId);
            query.and(q->{q.in(EmployeeDO::getPrimaryDepartmentId,ids);if(!candidateIds.isEmpty())q.or().in(EmployeeDO::getId,candidateIds);});
            var visible=employeeMapper.selectList(query); var visibleIds=visible.stream().map(EmployeeDO::getId).collect(java.util.stream.Collectors.toSet());
            visible.forEach(row->{if(ids.contains(row.getPrimaryDepartmentId()))people.computeIfAbsent(row.getPrimaryDepartmentId(),ignored->new java.util.HashSet<>()).add(row.getId());});
            members.stream().filter(row->visibleIds.contains(row.getEmployeeId())).forEach(row->people.computeIfAbsent(row.getDepartmentId(),ignored->new java.util.HashSet<>()).add(row.getEmployeeId()));
        }
        return rows.stream().map(row->new DepartmentItem(row.getId(),row.getParentId(),row.getCode(),row.getName(),row.getStatus(),row.getLeaderEmployeeId(),row.getParentId()==null?null:names.getOrDefault(row.getParentId(),"已删除部门"),canCount?(long)people.getOrDefault(row.getId(),java.util.Set.of()).size():null,row.getUpdatedAt())).toList();
    }

    private JobGradeItem jobGradeItem(JobGradeDO entity) {
        return new JobGradeItem(entity.getId(), entity.getCode(), entity.getName(), entity.getLevelValue(), entity.getDefaultRoleId(),
                entity.getStatus(), entity.getSource() == null ? "LOCAL" : entity.getSource(), entity.getUpdatedAt());
    }

    public record PageResult<T>(List<T> items, long total) { }
    public record EmployeeItem(Long id, String employeeNo, String displayName, String status,
                               Long primaryDepartmentId, Long jobGradeId, String internalMobile, String internalEmail, String source, List<DepartmentMembership> memberships, LocalDateTime updatedAt) { }
    public record DepartmentItem(Long id, Long parentId, String code, String name, String status, Long leaderEmployeeId, String parentName, Long employeeCount, LocalDateTime updatedAt) { }
    public record JobGradeItem(Long id, String code, String name, Integer levelValue, Long defaultRoleId, String status, String source, LocalDateTime updatedAt) { }
    public record DepartmentMembership(Long departmentId, Long jobGradeId, Boolean primary) { }
    public record EmployeeCommand(String employeeNo, String displayName, String status,
                                  Long primaryDepartmentId, Long jobGradeId, List<DepartmentMembership> memberships, String internalMobile, String internalEmail) {
        public EmployeeCommand(String employeeNo, String displayName, String status, Long primaryDepartmentId, Long jobGradeId, List<DepartmentMembership> memberships) { this(employeeNo, displayName, status, primaryDepartmentId, jobGradeId, memberships, null, null); }
    }
    /** 员工分页和组织筛选参数。 */
    public record EmployeeQuery(Integer page, Integer pageSize, String keyword, String status, Long departmentId, Long jobGradeId) {
        public EmployeeQuery { page = page == null ? 1 : page; pageSize = pageSize == null ? 20 : pageSize; }
    }
    public record DepartmentCommand(String code, String name, String status, Long parentId, Long leaderEmployeeId) { }
    public record JobGradeCommand(String code, String name, Integer levelValue, Long defaultRoleId, String status) { }
}
