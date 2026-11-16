package com.seeex.tech.web.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seeex.tech.web.rbac.entity.RoleResourceDO;
import org.apache.ibatis.annotations.Mapper;

/** 角色授权数据访问。 */
@Mapper
public interface RoleResourceMapper extends BaseMapper<RoleResourceDO> { }
