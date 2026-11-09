package com.myrecipe.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.myrecipe.global.exception.BusinessException;
import com.myrecipe.global.exception.ErrorCode;
import com.myrecipe.member.dto.SignupRequest;
import com.myrecipe.member.entity.Member;
import com.myrecipe.member.repository.MemberRepository;
import com.myrecipe.member.service.MemberService;
import com.myrecipe.support.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 통합 테스트: 진짜 MySQL로 "가짜로는 확인할 수 없던 것"을 확인한다.
// 흐름 테스트라 @Transactional을 붙이지 않는다(붙이면 커밋·readOnly 동작이 가려진다). 대신 테스트마다 데이터를 지운다.
class MemberSignupIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MemberService memberService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        memberRepository.deleteAll();
    }

    @Test
    void 회원가입하면_DB에_저장되고_비밀번호는_암호화되며_가입_시각이_기록된다() {
        // when
        memberService.signup(new SignupRequest("tofu@test.com", "password123", "두부요리사"));

        // then: 진짜 DB에서 다시 읽어 확인
        Member saved = memberRepository.findByEmail("tofu@test.com").orElseThrow();
        assertThat(saved.getId()).isNotNull();                                         // DB가 id를 채웠다
        assertThat(saved.getPassword()).isNotEqualTo("password123");                   // 평문이 아니다
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue(); // 하지만 맞는 비밀번호로 확인된다
        assertThat(saved.getCreatedAt()).isNotNull();                                  // JPA Auditing이 켜져 있다(11-05 기록)
    }

    @Test
    void 중복_확인을_건너뛰고_같은_이메일을_저장하면_DB_UNIQUE_제약이_막는다() {
        // given: 이미 가입된 회원
        memberService.signup(new SignupRequest("tofu@test.com", "password123", "두부요리사"));

        // when & then: 동시 가입에서 늦게 INSERT한 요청과 같은 상황(서비스의 exists 확인을 거치지 않고 바로 저장)
        Member sameEmail = Member.create("tofu@test.com", "encoded", "다른닉네임");
        assertThatThrownBy(() -> memberRepository.saveAndFlush(sameEmail))
                .isInstanceOf(DataIntegrityViolationException.class);   // 이 예외를 MemberService가 제약 이름으로 409로 바꾼다(아래 동시 가입 테스트)
    }

    @Test
    void 실험_읽기_전용_트랜잭션_안에서_저장하면_회원이_저장되지_않는다() {
        // 질문 3의 짐작("signup에서 @Transactional을 빼먹으면 클래스의 readOnly가 적용돼 저장이 안 된다")을 직접 확인한다.
        // given: 클래스에 붙은 @Transactional(readOnly = true)와 같은 조건
        TransactionTemplate readOnlyTx = new TransactionTemplate(transactionManager);
        readOnlyTx.setReadOnly(true);

        // when: 읽기 전용 트랜잭션 안에서 저장 시도 (예외가 나는지, 조용히 무시되는지도 실험 결과로 본다)
        Throwable thrown = catchThrowable(() -> readOnlyTx.executeWithoutResult(status ->
                memberRepository.save(Member.create("readonly@test.com", "encoded", "읽기전용"))));

        // then: 어떤 방식이든 DB에는 남지 않아야 한다
        assertThat(memberRepository.existsByEmail("readonly@test.com"))
                .as("읽기 전용 트랜잭션에서의 저장 결과 (발생한 예외: %s)", thrown)
                .isFalse();
    }

    @Test
    void 같은_이메일로_동시에_가입하면_한_명만_성공하고_나머지는_모두_EMAIL_DUPLICATED를_받는다() throws Exception {
        // given: 같은 이메일, 서로 다른 닉네임으로 5개의 가입 요청을 동시에 출발시킨다
        int requestCount = 5;
        ExecutorService pool = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<Member>> results = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            SignupRequest request = new SignupRequest("race@test.com", "password123", "동시가입" + i);
            results.add(pool.submit(() -> {
                startSignal.await();   // 모든 스레드가 준비되면 한꺼번에 출발
                return memberService.signup(request);
            }));
        }

        // when
        startSignal.countDown();
        int successCount = 0;
        List<Throwable> failures = new ArrayList<>();
        for (Future<Member> result : results) {
            try {
                result.get(30, TimeUnit.SECONDS);
                successCount++;
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            }
        }
        pool.shutdown();

        // then: exists 확인에서 막혔든, DB UNIQUE에서 막혔든 사용자는 모두 409 EMAIL_DUPLICATED를 받아야 한다(500 금지)
        assertThat(successCount).isEqualTo(1);
        assertThat(failures).hasSize(requestCount - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode")
                        .isEqualTo(ErrorCode.EMAIL_DUPLICATED));
        assertThat(memberRepository.count()).isEqualTo(1);
    }
}
