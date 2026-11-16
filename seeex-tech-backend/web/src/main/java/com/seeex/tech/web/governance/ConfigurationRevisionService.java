package com.seeex.tech.web.governance;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
/** 单调配置版本，集合为空时也能识别旧表单，锁与业务写入同事务。 */
@Service @RequiredArgsConstructor
public class ConfigurationRevisionService {
    private final ConfigurationRevisionMapper mapper;
    /** 查询版本，未创建过的配置为0；GET不写数据库。 */
    public long current(String key) { var row=mapper.selectById(key); return row==null?0:row.getRevision(); }
    /** CAS推进一次版本；调用者必须已经开启业务事务。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public void advance(String key,Long expected) {
        if(expected==null || expected<0 || expected==Long.MAX_VALUE) throw new BizException(ErrorCode.VALIDATION_FAILED,"请提供有效expectedVersion");
        if(mapper.selectById(key)==null) {
            var row=new ConfigurationRevisionDO(); row.setConfigKey(key); row.setRevision(0L);
            try { mapper.insert(row); } catch(DuplicateKeyException ex) { /* 并发首次初始化，仍须执行下面的版本CAS。 */ }
        }
        int count=mapper.update(null,Wrappers.<ConfigurationRevisionDO>lambdaUpdate().eq(ConfigurationRevisionDO::getConfigKey,key).eq(ConfigurationRevisionDO::getRevision,expected).set(ConfigurationRevisionDO::getRevision,expected+1));
        if(count!=1) throw new BizException(ErrorCode.CONFLICT,"配置已被其他管理员修改，请保留草稿并重新载入后确认");
    }
}
