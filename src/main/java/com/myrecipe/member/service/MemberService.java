package com.myrecipe.member.service;

import com.myrecipe.global.exception.BusinessException;
import com.myrecipe.global.exception.ErrorCode;
import com.myrecipe.member.dto.SignupRequest;
import com.myrecipe.member.entity.Member;
import com.myrecipe.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }

        Member member = Member.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname());
        try {
            // 위의 exists 확인과 INSERT 사이에 같은 값이 먼저 저장될 수 있다(동시 가입).
            // 그때는 DB UNIQUE 제약이 막는다. saveAndFlush로 INSERT를 지금 바로 보내서 위반을 이 자리에서 잡는다.
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw toDuplicateException(e);
        }
    }

    // 위반된 제약 이름으로 어떤 중복인지 구분한다. 알 수 없는 위반은 그대로 던져 500 + error 로그로 남긴다(CODE_CONVENTION).
    private RuntimeException toDuplicateException(DataIntegrityViolationException e) {
        String constraintName = violatedConstraintName(e);
        if (isConstraint(constraintName, Member.UK_EMAIL)) {
            return new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        if (isConstraint(constraintName, Member.UK_NICKNAME)) {
            return new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        return e;
    }

    private String violatedConstraintName(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }

    // MySQL 8은 제약 이름 앞에 테이블 이름을 붙여 알려 줄 수 있다(예: member.uk_member_email). 둘 다 같은 제약으로 본다.
    private boolean isConstraint(String actual, String expected) {
        return actual != null && (actual.equals(expected) || actual.endsWith("." + expected));
    }
}
