package com.seeex.tech.web.wecom;
import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.web.auth.ManagementAuthorizationService;
import com.seeex.tech.web.audit.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
/** 通讯录映射与只读预览接口，远端调用仅在用户明确发起时执行。 */
@RestController @RequestMapping("/api/wecom/directory") @RequiredArgsConstructor
public class WeComDirectoryController {
    private final WeComDirectoryService service;
    private final ManagementAuthorizationService authorization;
    private final com.seeex.tech.web.organization.OrganizationGrantGuard grants;
    /** 查看映射与有效职级选项。 */
    @GetMapping("/mappings") public ApiResponse<Map<String,Object>> list(Authentication auth) { authorization.require(auth,"wecom:config:view"); return ApiResponse.ok(service.mappings()); }
    /** 新增映射。 */
    @PostMapping("/mappings") @AuditLog(action="CREATE_GRADE_MAPPING",resourceType="WECOM")
    public ApiResponse<WeComGradeMappingDO> create(@RequestBody WeComDirectoryService.MappingCommand command,Authentication auth) { authorization.require(auth,"wecom:config:update"); grants.mappedGrades(auth,null,command.jobGradeId()); return ApiResponse.ok(service.save(null,command)); }
    /** 编辑或停用映射。 */
    @PutMapping("/mappings/{id}") @AuditLog(action="UPDATE_GRADE_MAPPING",resourceType="WECOM")
    public ApiResponse<WeComGradeMappingDO> update(@PathVariable long id,@RequestBody WeComDirectoryService.MappingCommand command,Authentication auth) { authorization.require(auth,"wecom:config:update"); grants.mappedGrades(auth,service.previousGrade(id),command.jobGradeId()); return ApiResponse.ok(service.save(id,command)); }
    /** 删除映射，历史职级保留。 */
    @DeleteMapping("/mappings/{id}") @AuditLog(action="DELETE_GRADE_MAPPING",resourceType="WECOM")
    public ApiResponse<Void> delete(@PathVariable long id,Authentication auth) { authorization.require(auth,"wecom:config:update"); grants.mappedGrades(auth,service.previousGrade(id),null); service.delete(id); return ApiResponse.ok(null); }
    /** 只读预览；缺少读取或执行权限均拒绝，接口不会写组织。 */
    @PostMapping("/preview")
    public ApiResponse<WeComDirectoryService.Preview> preview(Authentication auth) { authorization.require(auth,"wecom:sync:view"); authorization.require(auth,"wecom:sync:run"); return ApiResponse.ok(service.preview()); }
}
