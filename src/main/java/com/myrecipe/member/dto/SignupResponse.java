package com.myrecipe.member.dto;

import com.myrecipe.member.entity.Member;

// API_SPEC "회원가입" 응답. password는 해시여도 응답에 넣지 않는다.
public record SignupResponse(Long memberId, String email, String nickname) {

    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getId(), member.getEmail(), member.getNickname());
    }
}