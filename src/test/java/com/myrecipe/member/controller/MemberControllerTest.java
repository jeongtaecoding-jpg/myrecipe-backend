package com.myrecipe.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myrecipe.global.config.SecurityConfig;
import com.myrecipe.global.exception.BusinessException;
import com.myrecipe.global.exception.ErrorCode;
import com.myrecipe.member.dto.SignupRequest;
import com.myrecipe.member.entity.Member;
import com.myrecipe.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

// 컨트롤러 테스트: 웹 계층(요청 검증 → 상태 코드 → 응답 JSON)과 보안 설정만 띄운다.
// SecurityConfig를 함께 불러와서 "회원가입은 로그인 없이 호출 가능"(permitAll)이 깨지면 이 테스트가 잡는다.
// 서비스는 가짜(@MockitoBean)라서 DB INSERT·UNIQUE 제약은 여기서 확인하지 않는다(통합 테스트에서).
@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
class MemberControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MemberService memberService;

    static final String VALID_BODY = """
            {"email":"tofu@test.com","password":"password123","nickname":"두부요리사"}
            """;

    @Test
    void 회원가입에_성공하면_201과_회원_정보를_응답하고_비밀번호는_응답에_없다() throws Exception {
        // given
        Member member = Member.create("tofu@test.com", "encoded-password", "두부요리사");
        ReflectionTestUtils.setField(member, "id", 1L);   // 저장 후 DB가 채워 주는 id를 흉내 낸다
        given(memberService.signup(any(SignupRequest.class))).willReturn(member);

        // when & then (토큰 없이 호출 → permitAll이 동작하는지도 함께 확인)
        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(1))
                .andExpect(jsonPath("$.email").value("tofu@test.com"))
                .andExpect(jsonPath("$.nickname").value("두부요리사"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void 입력값이_규칙에_맞지_않으면_400과_fieldErrors를_응답하고_서비스를_호출하지_않는다() throws Exception {
        // given: 이메일 형식 오류 + 비밀번호 8자 미만
        String invalidBody = """
                {"email":"not-an-email","password":"short","nickname":"두부요리사"}
                """;

        // when & then
        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
        then(memberService).should(never()).signup(any(SignupRequest.class));
    }

    @Test
    void 이메일이_중복이면_409와_EMAIL_DUPLICATED를_응답한다() throws Exception {
        // given
        given(memberService.signup(any(SignupRequest.class)))
                .willThrow(new BusinessException(ErrorCode.EMAIL_DUPLICATED));

        // when & then
        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
    }

    @Test
    void 허용_목록에_없는_주소를_토큰_없이_호출하면_보안_설정이_막는다() throws Exception {
        // 보안 설정이 이 테스트에 실제로 적용됐는지 확인한다. 적용되지 않았다면 컨트롤러가 없는 주소라 404가 나온다.
        // 지금은 인증 실패 응답이 401이 아니라 403이다(알고 있는 한계). ROADMAP 1단계 "필터 단계 에러(401)"를 끝내면 401로 바꾼다.
        mockMvc.perform(post("/api/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
