package com.seeex.tech.file.controller;

import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.file.service.FileMetadataService;
import com.seeex.tech.file.service.OssStorageService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 删除归属检查必须先于存储副作用；本轮只编写不执行。 */
class FileDeletionContractTest {
    @Test
    void unauthorizedSingleDeleteNeverTouchesStorage() {
        var storage = mock(OssStorageService.class); var metadata = mock(FileMetadataService.class);
        when(metadata.fileName("other", "1", "default")).thenThrow(new BizException(ErrorCode.FORBIDDEN));
        assertThrows(BizException.class, () -> new FileController(storage, metadata).delete("other", "1", "default"));
        verify(storage, never()).delete(anyString());
    }
    @Test
    void batchValidatesEveryOwnerBeforeDeletingAnyObject() {
        var storage = mock(OssStorageService.class); var metadata = mock(FileMetadataService.class);
        when(metadata.fileName("mine", "1", "default")).thenReturn("mine.pdf");
        when(metadata.fileName("other", "1", "default")).thenThrow(new BizException(ErrorCode.FORBIDDEN));
        assertThrows(BizException.class, () -> new FileController(storage, metadata).batchDelete(List.of("mine", "other"), "1", "default"));
        verify(storage, never()).delete(anyString());
    }
}
