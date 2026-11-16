package com.seeex.tech.web.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seeex.tech.web.rbac.entity.RoleDO;
import org.apache.ibatis.annotations.Mapper;

/** 角色数据访问。 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleDO> { }
