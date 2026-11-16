package com.seeex.tech.web.wecom;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/** 企业微信配置持久层。 */
@Mapper
public interface WeComSettingsMapper extends BaseMapper<WeComSettingsDO> { }
