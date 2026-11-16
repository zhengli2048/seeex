package com.seeex.tech.web.registry.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seeex.tech.web.registry.entity.SsoExchangeCodeDO;
import org.apache.ibatis.annotations.Mapper;

/** SSO 交换码持久层。 */
@Mapper
public interface SsoExchangeCodeMapper extends BaseMapper<SsoExchangeCodeDO> { }
