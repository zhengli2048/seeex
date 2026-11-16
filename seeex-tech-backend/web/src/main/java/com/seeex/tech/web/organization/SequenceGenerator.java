package com.seeex.tech.web.organization;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 日期+序列业务编号生成器。 */
@Service
public class SequenceGenerator {

    /** 日期格式。 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 序列长度。 */
    private static final int SEQ_LENGTH = 8;

    /** 生成下一个业务编号：前缀 + 日期 + 8 位当日序列。 */
    public String next(String prefix, long currentMaxSeq) {
        return prefix + LocalDate.now().format(DATE_FORMAT) + String.format("%08d", currentMaxSeq + 1);
    }

    /** 从现有编号列表中提取指定前缀当日已用的最大序列，无则返回 0。 */
    public long maxSeq(String prefix, List<String> existingCodes) {
        String datePrefix = prefix + LocalDate.now().format(DATE_FORMAT);
        return existingCodes.stream()
                .filter(code -> code != null && code.startsWith(datePrefix) && code.length() == datePrefix.length() + SEQ_LENGTH)
                .mapToLong(code -> Long.parseLong(code.substring(datePrefix.length())))
                .max().orElse(0L);
    }
}
