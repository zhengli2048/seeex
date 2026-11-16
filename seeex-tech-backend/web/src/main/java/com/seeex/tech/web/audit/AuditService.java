package com.seeex.tech.web.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** 审计日志记录与查询服务。 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    @org.springframework.beans.factory.annotation.Autowired private com.seeex.tech.web.organization.mapper.EmployeeMapper employees;
    @org.springframework.beans.factory.annotation.Autowired private com.seeex.tech.web.registry.mapper.ApplicationMapper applications;

    /** 记录一条审计日志。 */
    public void record(String operatorEmployeeId, String action, String resourceType, Long resourceId,
                       String sourceIp, String result, String requestSummary, String beforeJson, String afterJson) {
        AuditLogDO entity = new AuditLogDO();
        entity.setLogNo(UUID.randomUUID().toString().replace("-", ""));
        entity.setOperatorEmployeeId(operatorEmployeeId);
        entity.setAction(action);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setApplicationCode("platform");
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attrs) {
            var request=attrs.getRequest(); String path=request.getRequestURI(); String key=request.getParameter("objectKey"); String resourceKey=key==null?path:"file:"+key; entity.setResourceKey(resourceKey.substring(0,Math.min(512,resourceKey.length())));
            var match=java.util.regex.Pattern.compile("/platform/apps/([^/]+)").matcher(path);
            Object verified=request.getAttribute("seeex.file.application");
            String app=verified instanceof String value?value:match.find()?match.group(1):request.getParameter("applicationCode");
            if(app!=null && app.matches("[a-z][a-z0-9-]{1,127}")) entity.setApplicationCode(app);
            var target=java.util.regex.Pattern.compile("/(\\d+)(?:/[^/]+)?$").matcher(path);
            if(resourceId==null && target.find()) { try { entity.setResourceId(Long.valueOf(target.group(1))); } catch(NumberFormatException ignored) { /* 非主键范围不作为ID记录。 */ } }
            String requestId=request.getHeader("X-Request-Id");
            if(requestId!=null && requestId.matches("[A-Za-z0-9._:-]{1,128}")) entity.setRequestId(requestId);
        }
        entity.setSourceIp(sourceIp);
        entity.setResult(result);
        entity.setRequestSummary(requestSummary);
        entity.setBeforeJson(beforeJson);
        entity.setAfterJson(afterJson);
        entity.setCreatedAt(java.time.LocalDateTime.now());
        auditLogMapper.insert(entity);
    }

    /** 分页查询审计日志。 */
    public PageResult list(int page, int pageSize, String operator, String action, String resourceType) {
        LambdaQueryWrapper<AuditLogDO> query = new LambdaQueryWrapper<AuditLogDO>()
                .eq(operator != null && !operator.isBlank(), AuditLogDO::getOperatorEmployeeId, operator)
                .eq(action != null && !action.isBlank(), AuditLogDO::getAction, action)
                .eq(resourceType != null && !resourceType.isBlank(), AuditLogDO::getResourceType, resourceType)
                .orderByDesc(AuditLogDO::getId);
        Page<AuditLogDO> result = auditLogMapper.selectPage(new Page<>(page, pageSize), query);
        return new PageResult(result.getRecords(), result.getTotal());
    }

    /** 按相同条件查询列表与导出，校验时间顺序。 */
    private LambdaQueryWrapper<AuditLogDO> query(AuditQuery filter) {
        var query = new LambdaQueryWrapper<AuditLogDO>()
            .eq(filter.operator()!=null && !filter.operator().isBlank(),AuditLogDO::getOperatorEmployeeId,filter.operator())
            .eq(filter.action()!=null && !filter.action().isBlank(),AuditLogDO::getAction,filter.action())
            .eq(filter.resourceType()!=null && !filter.resourceType().isBlank(),AuditLogDO::getResourceType,filter.resourceType())
            .eq(filter.applicationCode()!=null && !filter.applicationCode().isBlank(),AuditLogDO::getApplicationCode,filter.applicationCode())
            .eq(filter.resourceKey()!=null && !filter.resourceKey().isBlank(),AuditLogDO::getResourceKey,filter.resourceKey())
            .eq(filter.result()!=null && !filter.result().isBlank(),AuditLogDO::getResult,filter.result());
        if(filter.result()!=null && !filter.result().isBlank() && !java.util.List.of("SUCCESS","FAILED").contains(filter.result())) throw invalid("结果筛选值无效");
        try {
            var from=filter.from()==null || filter.from().isBlank()?null:java.time.LocalDateTime.parse(filter.from().replace(' ','T'));
            var to=filter.to()==null || filter.to().isBlank()?null:java.time.LocalDateTime.parse(filter.to().replace(' ','T'));
            if(from!=null && to!=null && from.isAfter(to)) throw invalid("开始时间不能晚于结束时间");
            if(from!=null) query.ge(AuditLogDO::getCreatedAt,from); if(to!=null) query.le(AuditLogDO::getCreatedAt,to);
        } catch(java.time.format.DateTimeParseException ex) { throw invalid("时间格式不合法"); }
        return query.orderByDesc(AuditLogDO::getId);
    }
    /** 分页读取审计，包含操作者名称。 */
    public PageResult list(AuditQuery filter) {
        var result=auditLogMapper.selectPage(new Page<>(Math.max(1,filter.page()),Math.min(100,Math.max(1,filter.pageSize()))),query(filter));
        names(result.getRecords()); return new PageResult(result.getRecords(),result.getTotal());
    }
    /** 远程搜索字典每类最多50项；当前选择额外保留，不全表传输。 */
    public java.util.Map<String,Object> options(OptionQuery filter) {
        var employeeQuery=com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.seeex.tech.web.organization.entity.EmployeeDO>lambdaQuery().select(com.seeex.tech.web.organization.entity.EmployeeDO::getId,com.seeex.tech.web.organization.entity.EmployeeDO::getDisplayName).like(filter.operatorKeyword()!=null && !filter.operatorKeyword().isBlank(),com.seeex.tech.web.organization.entity.EmployeeDO::getDisplayName,filter.operatorKeyword()).orderByAsc(com.seeex.tech.web.organization.entity.EmployeeDO::getId);
        var operators=new java.util.ArrayList<>(employees.selectPage(new Page<>(1,50,false),employeeQuery).getRecords().stream().map(row->new OperatorOption(String.valueOf(row.getId()),row.getDisplayName())).toList());
        if(filter.operator()!=null && operators.stream().noneMatch(row->row.id().equals(filter.operator()))) { try { var selected=employees.selectById(Long.valueOf(filter.operator())); if(selected!=null) operators.add(new OperatorOption(filter.operator(),selected.getDisplayName())); } catch(NumberFormatException ex) { /* 历史系统标识不是员工ID。 */ } }
        var appQuery=com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.seeex.tech.web.registry.entity.ApplicationDO>lambdaQuery().select(com.seeex.tech.web.registry.entity.ApplicationDO::getAppCode,com.seeex.tech.web.registry.entity.ApplicationDO::getAppName).like(filter.appKeyword()!=null && !filter.appKeyword().isBlank(),com.seeex.tech.web.registry.entity.ApplicationDO::getAppName,filter.appKeyword()).orderByAsc(com.seeex.tech.web.registry.entity.ApplicationDO::getAppCode);
        var apps=new java.util.ArrayList<>(applications.selectPage(new Page<>(1,50,false),appQuery).getRecords().stream().map(row->new AppOption(row.getAppCode(),row.getAppName())).toList());
        apps.add(new AppOption("platform","基础平台"));
        if(filter.applicationCode()!=null && apps.stream().noneMatch(row->row.appCode().equals(filter.applicationCode()))) { var selected=applications.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.seeex.tech.web.registry.entity.ApplicationDO>lambdaQuery().eq(com.seeex.tech.web.registry.entity.ApplicationDO::getAppCode,filter.applicationCode())); if(selected!=null) apps.add(new AppOption(selected.getAppCode(),selected.getAppName())); }
        var keyQuery=new LambdaQueryWrapper<AuditLogDO>().select(AuditLogDO::getResourceKey).isNotNull(AuditLogDO::getResourceKey).like(filter.resourceKeyword()!=null && !filter.resourceKeyword().isBlank(),AuditLogDO::getResourceKey,filter.resourceKeyword()).groupBy(AuditLogDO::getResourceKey).orderByAsc(AuditLogDO::getResourceKey);
        var keys=new java.util.ArrayList<>(auditLogMapper.selectPage(new Page<>(1,50,false),keyQuery).getRecords().stream().map(AuditLogDO::getResourceKey).toList());
        if(filter.resourceKey()!=null && !keys.contains(filter.resourceKey())) keys.add(filter.resourceKey());
        return java.util.Map.of("operators",operators,"applications",apps,"resources",keys);
    }
    /** 搜索关键字与当前选中项，所有SQL均使用参数绑定。 */
    public record OptionQuery(String operatorKeyword,String appKeyword,String resourceKeyword,String operator,String applicationCode,String resourceKey) { }
    /** 最多导出10000行；超过上限明确拒绝，绝不静默截断。 */
    public byte[] export(AuditQuery filter) {
        var result=auditLogMapper.selectPage(new Page<>(1,10001),query(filter));
        if(result.getTotal()>10000) throw invalid("超过10000条导出上限，请缩小筛选范围");
        StringBuilder csv=new StringBuilder("\ufeff时间,操作人,操作人ID,应用,动作,对象类型,资源标识,结果,来源IP,请求编号\r\n");
        names(result.getRecords());
        for(var row:result.getRecords()) { var cells=java.util.Arrays.asList(row.getCreatedAt(),row.getOperatorName(),row.getOperatorEmployeeId(),row.getApplicationCode(),row.getAction(),row.getResourceType(),row.getResourceKey(),row.getResult(),row.getSourceIp(),row.getRequestId()); csv.append(cells.stream().map(this::csv).collect(java.util.stream.Collectors.joining(","))).append("\r\n"); }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    private String csv(Object input) {
        String text=input==null?"":String.valueOf(input);
        if(text.stripLeading().matches("(?s)^[=+@\\-].*") || text.startsWith("\t") || text.startsWith("\r") || text.startsWith("\n")) text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }
    private void names(List<AuditLogDO> rows) {
        var ids=new java.util.HashSet<Long>();
        for(var row:rows) { try { ids.add(Long.valueOf(row.getOperatorEmployeeId())); } catch(NumberFormatException ex) { row.setOperatorName("系统"); } }
        var names=new java.util.HashMap<String,String>(); var all=new java.util.ArrayList<>(ids);
        final int chunkSize=500;
        for(int offset=0;offset<all.size();offset+=chunkSize) employees.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.seeex.tech.web.organization.entity.EmployeeDO>lambdaQuery().select(com.seeex.tech.web.organization.entity.EmployeeDO::getId,com.seeex.tech.web.organization.entity.EmployeeDO::getDisplayName).in(com.seeex.tech.web.organization.entity.EmployeeDO::getId,all.subList(offset,Math.min(all.size(),offset+chunkSize)))).forEach(row->names.put(String.valueOf(row.getId()),row.getDisplayName()));
        rows.forEach(row->{ if(row.getOperatorName()==null) row.setOperatorName(names.getOrDefault(row.getOperatorEmployeeId(),"历史操作者")); });
    }
    private com.seeex.tech.common.exception.BizException invalid(String message) { return new com.seeex.tech.common.exception.BizException(com.seeex.tech.common.api.ErrorCode.VALIDATION_FAILED,message); }
    /** 审计选择项。 */
    public record OperatorOption(String id,String name) { }
    /** 应用选择项。 */
    public record AppOption(String appCode,String name) { }
    /** 所有筛选在列表和导出共享。 */
    public record AuditQuery(Integer page,Integer pageSize,String operator,String action,String resourceType,String from,String to,String applicationCode,String resourceKey,String result) {
        public AuditQuery { page=page==null?1:page; pageSize=pageSize==null?20:pageSize; }
        public AuditQuery(Integer page,Integer pageSize,String operator,String action,String resourceType,String from,String to) { this(page,pageSize,operator,action,resourceType,from,to,null,null,null); }
    }

    /** 查询单条审计日志详情。 */
    public AuditLogDO detail(long id) {
        var row=auditLogMapper.selectById(id); if(row!=null) names(List.of(row)); return row;
    }

    public record PageResult(List<AuditLogDO> items, long total) { }
}
