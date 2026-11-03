package com.myrecipe.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Spring 컨텍스트(DB·Redis) 없이 핸들러만 확인하는 standalone MockMvc 테스트
class GlobalExceptionHandlerTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 업무_예외는_ErrorCode의_상태와_코드로_응답한다() throws Exception {
        // given & when & then
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("레시피를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void 입력값_검증에_실패하면_fieldErrors가_포함된다() throws Exception {
        // given & when & then
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    void 잘못된_JSON이면_400을_응답한다() throws Exception {
        // given & when & then
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{깨진 json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 예상하지_못한_예외는_500이고_원인이_응답에_없다() throws Exception {
        // given & when & then
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("일시적인 오류가 발생했습니다."));
    }

    @RestController
    static class DummyController {

        record Body(@NotBlank String title) {
        }

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }

        @PostMapping("/test/valid")
        void valid(@Valid @RequestBody Body body) {
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("내부 사정: SQL 등");
        }
    }
}
