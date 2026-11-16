package com.seeex.tech.web.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seeex.tech.web.rbac.entity.ResourceDO;
import org.apache.ibatis.annotations.Mapper;

/** 资源数据访问。 */
@Mapper
public interface ResourceMapper extends BaseMapper<ResourceDO> { }
