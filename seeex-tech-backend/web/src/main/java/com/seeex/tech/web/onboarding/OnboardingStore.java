package com.seeex.tech.web.onboarding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
/** 聚合本业务持久层，服务仍负责事务和对象权限。 */
@Component @RequiredArgsConstructor
public class OnboardingStore {
    final OnboardingRequestMapper requests;
    final OnboardingRevisionMapper revisions;
    final OnboardingTaskMapper tasks;
    final OnboardingLogMapper logs;
    final OnboardingDefinitionMapper definitions;
    final OnboardingDeliveryMapper deliveries;
    final OnboardingMessageMapper messages;
    final OnboardingNoticeMapper notices;
    final OnboardingActionMapper actions;
    final OnboardingReservationMapper reservations;
    final OnboardingBindingMapper bindings;
    final OnboardingParticipantMapper participants;
}
