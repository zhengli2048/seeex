package com.seeex.tech.web.onboarding;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.registry.ApplicationRegistryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 身份边界和交付绑定的回归源码；不代替真实并发、数据库或浏览器验收。 */
class OnboardingSecurityContractTest {
    @Test void applicantCannotApproveOwnRequestEvenWithTaskAndGlobalPermission() {
        var directory=mock(OnboardingDirectory.class); var access=new OnboardingAccess(mock(OnboardingStore.class),directory,mock(OnboardingDefinitionService.class),mock(OnboardingJson.class)); var request=new OnboardingRequestDO(); request.setApplicantId(7L); var task=new OnboardingTaskDO(); task.setCandidateId(7L);
        assertFalse(access.reviewer(7L,request,task)); verifyNoInteractions(directory);
    }
    @Test void globalAdministratorCannotReadAnotherApplicantsDelivery() {
        var access=mock(OnboardingAccess.class); var directory=mock(OnboardingDirectory.class); var request=new OnboardingRequestDO(); request.setApplicantId(1L); request.setState("PENDING_RECEIPT"); when(access.request(12L)).thenReturn(request);
        var service=new OnboardingDeliveryService(mock(OnboardingStore.class),access,directory,mock(OnboardingSecretCodec.class),mock(OnboardingJson.class),mock(OnboardingNotificationService.class),mock(ApplicationRegistryService.class));
        assertThrows(BizException.class,()->service.view(2L,12L));
    }
    @Test void ciphertextIsBoundToRequestRecipientAndDeliveryVersion() {
        var codec=new OnboardingSecretCodec(); ReflectionTestUtils.setField(codec,"key",Base64.getEncoder().encodeToString(new byte[32])); ReflectionTestUtils.setField(codec,"keyId","test-only");
        String encrypted=codec.encrypt("synthetic-test-credential","12:7:1"); assertEquals("synthetic-test-credential",codec.decrypt(encrypted,"test-only","12:7:1")); assertThrows(BizException.class,()->codec.decrypt(encrypted,"test-only","12:8:1")); assertThrows(BizException.class,()->codec.decrypt(encrypted,"test-only","12:7:2"));
    }
    @Test void noFallbackDeliveryKeyExists() { assertThrows(BizException.class,()->new OnboardingSecretCodec().requireConfigured()); }
}
