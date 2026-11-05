package com.myrecipe.member.controller;

import com.myrecipe.member.dto.SignupRequest;
import com.myrecipe.member.dto.SignupResponse;
import com.myrecipe.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Controller는 "검증 → 서비스 호출 → 응답 변환"만 한다. 중복 확인·인코딩 같은 업무 로직은 MemberService에 있다.
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    // API_SPEC "회원가입": 성공 시 201 Created + { memberId, email, nickname }
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = SignupResponse.from(memberService.signup(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
