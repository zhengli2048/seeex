package com.seeex.tech.web.onboarding;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.registry.ApplicationRegistryService;
import com.seeex.tech.web.registry.mapper.ApplicationMapper;
import com.seeex.tech.web.governance.ApplicationAdminMapper;
import com.seeex.tech.web.governance.ApplicationAdminDO;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.List;
import java.util.Set;
import static com.seeex.tech.web.onboarding.OnboardingContract.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
/** 校验真实Spring事务代理会在交付失败时请求回滚；未运行，也不代替MySQL回滚验证。 */
class OnboardingAtomicFulfillmentTest {
    @Test void encryptedDeliveryFailureRollsBackRegistrationAndAdministratorWrites() {
        var reservations=mock(OnboardingReservationMapper.class); var store=new OnboardingStore(mock(OnboardingRequestMapper.class),mock(OnboardingRevisionMapper.class),mock(OnboardingTaskMapper.class),mock(OnboardingLogMapper.class),mock(OnboardingDefinitionMapper.class),mock(OnboardingDeliveryMapper.class),mock(OnboardingMessageMapper.class),mock(OnboardingNoticeMapper.class),mock(OnboardingActionMapper.class),reservations,mock(OnboardingBindingMapper.class),mock(OnboardingParticipantMapper.class));
        var directory=mock(OnboardingDirectory.class);var access=mock(OnboardingAccess.class);var validation=mock(OnboardingValidation.class);var definitions=mock(OnboardingDefinitionService.class);var notifications=mock(OnboardingNotificationService.class);var delivery=mock(OnboardingDeliveryService.class);var json=mock(OnboardingJson.class);var registry=mock(ApplicationRegistryService.class);var applications=mock(ApplicationMapper.class);var admins=mock(ApplicationAdminMapper.class);
        when(access.previous(eq(2L),eq("COMPLETE"),anyString(),any(),eq(10L))).thenReturn(null);
        var request=new OnboardingRequestDO();request.setId(10L);request.setState("FULFILLING");request.setFulfillerId(2L);request.setApplicantId(1L);request.setRowVersion(5L);request.setAppCode("demo-app");request.setDataJson("form");request.setFulfillmentJson("config");when(access.view(2L,10L)).thenReturn(request);when(access.fulfillmentCandidate(2L,request)).thenReturn(true);
        var form=new ApplicationForm("应用","demo-app","说明","用途","WEB",Set.of("WEB"),"TEST",List.of("app.example.com"),List.of(1L),"INTERNAL",List.of());var config=new FulfillmentForm("https://app.example.com","","","","","/sso/callback","");when(json.read("form",ApplicationForm.class)).thenReturn(form);when(json.read("config",FulfillmentForm.class)).thenReturn(config);
        var reservation=new OnboardingReservationDO();reservation.setAppCode("demo-app");reservation.setRequestId(10L);when(reservations.selectById("demo-app")).thenReturn(reservation);when(applications.selectCount(any())).thenReturn(0L);
        var registration=new ApplicationRegistryService.AppRegistration(null,"synthetic-ci","synthetic-service");when(registry.registerForRequest(any(),eq(10L))).thenReturn(registration);doThrow(new BizException(ErrorCode.BUSINESS_ERROR,"模拟加密交付失败")).when(delivery).create(request,registration);
        var target=new OnboardingService(store,directory,access,validation,definitions,notifications,delivery,json,registry,applications,admins);var manager=mock(PlatformTransactionManager.class);var transaction=mock(TransactionStatus.class);when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction);var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));var service=(OnboardingService)proxy.getProxy();
        assertThrows(BizException.class,()->service.complete(2L,10L,new ActionCommand(5L,null,null,null,null,null),"synthetic-complete-key"));verify(admins).insert(any(ApplicationAdminDO.class));verify(manager).rollback(transaction);verify(manager,never()).commit(any());verify(access,never()).remember(anyLong(),anyString(),anyString(),any(),anyLong());
    }
}
