package com.myrecipe.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.myrecipe.global.exception.BusinessException;
import com.myrecipe.global.exception.ErrorCode;
import com.myrecipe.member.dto.SignupRequest;
import com.myrecipe.member.entity.Member;
import com.myrecipe.member.repository.MemberRepository;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

// 서비스 단위 테스트: DB·스프링 없이 MemberService의 판단(중복 확인 → 암호화 → 저장)만 확인한다.
// DB에 실제로 INSERT되는지, UNIQUE 제약이 동작하는지는 이 테스트로 알 수 없다(통합 테스트에서 확인).
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    MemberRepository memberRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    MemberService memberService;

    SignupRequest request = new SignupRequest("tofu@test.com", "password123", "두부요리사");

    @Test
    void 회원가입에_성공하면_비밀번호를_암호화해서_저장한다() {
        // given
        given(memberRepository.existsByEmail("tofu@test.com")).willReturn(false);
        given(memberRepository.existsByNickname("두부요리사")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("encoded-password");
        given(memberRepository.saveAndFlush(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Member saved = memberService.signup(request);

        // then
        assertThat(saved.getEmail()).isEqualTo("tofu@test.com");
        assertThat(saved.getNickname()).isEqualTo("두부요리사");
        assertThat(saved.getPassword()).isEqualTo("encoded-password");   // 평문이 아니라 암호화된 값
        then(memberRepository).should().saveAndFlush(any(Member.class));
    }

    @Test
    void 이메일이_이미_있으면_EMAIL_DUPLICATED_예외가_발생하고_저장하지_않는다() {
        // given
        given(memberRepository.existsByEmail("tofu@test.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_DUPLICATED);
        then(memberRepository).should(never()).saveAndFlush(any(Member.class));
    }

    @Test
    void 닉네임이_이미_있으면_NICKNAME_DUPLICATED_예외가_발생하고_저장하지_않는다() {
        // given
        given(memberRepository.existsByEmail("tofu@test.com")).willReturn(false);
        given(memberRepository.existsByNickname("두부요리사")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NICKNAME_DUPLICATED);
        then(memberRepository).should(never()).saveAndFlush(any(Member.class));
    }

    @Test
    void 중복_확인을_통과했는데_DB의_이메일_UNIQUE에_걸리면_EMAIL_DUPLICATED로_바꾼다() {
        // given: 동시 가입 — exists 확인 때는 없었지만, 저장 순간 다른 요청이 먼저 저장함
        given(memberRepository.existsByEmail("tofu@test.com")).willReturn(false);
        given(memberRepository.existsByNickname("두부요리사")).willReturn(false);
        given(memberRepository.saveAndFlush(any(Member.class))).willThrow(uniqueViolation("member.uk_member_email"));

        // when & then
        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_DUPLICATED);
    }

    @Test
    void 중복_확인을_통과했는데_DB의_닉네임_UNIQUE에_걸리면_NICKNAME_DUPLICATED로_바꾼다() {
        // given
        given(memberRepository.existsByEmail("tofu@test.com")).willReturn(false);
        given(memberRepository.existsByNickname("두부요리사")).willReturn(false);
        given(memberRepository.saveAndFlush(any(Member.class))).willThrow(uniqueViolation("uk_member_nickname"));

        // when & then
        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NICKNAME_DUPLICATED);
    }

    @Test
    void 알_수_없는_제약_위반은_바꾸지_않고_그대로_던진다() {
        // given: 이메일·닉네임이 아닌 다른 제약 위반 → 우리가 모르는 문제이므로 500 + error 로그로 남겨야 한다
        given(memberRepository.existsByEmail("tofu@test.com")).willReturn(false);
        given(memberRepository.existsByNickname("두부요리사")).willReturn(false);
        given(memberRepository.saveAndFlush(any(Member.class))).willThrow(uniqueViolation("some_other_constraint"));

        // when & then
        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // 스프링이 DB UNIQUE 위반을 감싸서 던지는 모양을 흉내 낸다 (안쪽에 Hibernate가 찾은 제약 이름이 들어 있다)
    private DataIntegrityViolationException uniqueViolation(String constraintName) {
        return new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException("Duplicate entry"), constraintName));
    }
}
