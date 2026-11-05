# 작업일지 — 2026-11-05

> 이전 기록은 `Work Log_1st_weeks.md`, 단계별 할 일은 `docs/ROADMAP.md`, 문제 해결 과정은 `TROUBLESHOOTING.md` 참고.

## 오늘의 위치

- 1단계(공통 기반 + 회원가입/로그인) 진행 중
- 오늘 끝낸 것: `MemberController` 작성, 지금까지 코드 복습
- 다음: 회원가입 테스트 (컨트롤러 테스트 방식 결정 대기)

---

## 1. 진행도 확인

ROADMAP과 `src` 코드를 비교했고 일치했다.

| 영역 | 파일 |
|---|---|
| 시작점 | `MyrecipeApplication` |
| global | `BaseTimeEntity`, `JpaConfig`, `ErrorCode`, `BusinessException`, `ErrorResponse`, `GlobalExceptionHandler`, `SecurityConfig` |
| member | `Member`, `MemberRepository`, `SignupRequest`, `SignupResponse`, `MemberService` |
| 테스트 | `GlobalExceptionHandlerTest`(4개), `MyrecipeApplicationTests` |

## 2. 오늘 작성한 코드: `MemberController`

`src/main/java/com/myrecipe/member/controller/MemberController.java`

```java
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = SignupResponse.from(memberService.signup(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

- 컨트롤러는 "검증(`@Valid`) → 서비스 호출 → Entity를 DTO로 변환 → 201 응답"만 한다. 업무 로직은 `MemberService`에 있다.
- `@Valid`를 빼면 검증이 조용히 건너뛰어져 빈 값이 서비스까지 내려간다.
- **확인한 것**: `./gradlew compileJava` 통과.
- **확인하지 않은 것**: 실행, 요청 테스트. 경로·201·응답 필드가 API_SPEC과 맞는지는 코드를 읽어 비교했을 뿐 테스트로 증명하지 않았다.

## 3. 코드 복습 (요청 흐름 `POST /api/members/signup`)

```
요청 JSON
 → Security 필터: permitAll이라 통과
 → Controller: JSON → SignupRequest, @Valid
      └ 실패 → GlobalExceptionHandler → 400 + fieldErrors
 → Service.signup: 중복 확인
      └ 중복 → BusinessException → 핸들러 → 409
 → BCrypt 인코딩 → Member.create → save (INSERT, createdAt 자동)
 → Controller: SignupResponse.from → 201
```

### 확인 질문과 답

| # | 질문 | 내 답 | 정답 요약 |
|---|---|---|---|
| 1 | `@EnableJpaAuditing`이 없으면? | `@Configuration` 등이 동작 안 한다고 답함 (부분 오답) | `@Configuration`은 정상. `@CreatedDate`·`@LastModifiedDate`가 안 채워져 `createdAt`이 null → `nullable=false`라서 **`save` 시점에 NOT NULL 오류**. 원인(`JpaConfig`)과 증상(`save`)의 위치가 다르다 |
| 2 | `ResponseEntityExceptionHandler`를 상속하지 않으면 없는 URL이 왜 500? | 모르겠음 | Spring은 예외 타입이 **가장 가까운** 핸들러를 고른다. `NoResourceFoundException` 등도 `Exception`의 하위 타입이라 `Exception.class` 핸들러밖에 없으면 거기서 500. 상속하면 구체적 핸들러가 먼저 선택됨 |
| 3 | `exists` 확인이 있는데 `unique`가 왜 필요? | 모르겠음 | 확인과 INSERT 사이의 틈 때문에 동시 가입을 자바 코드로 막을 수 없다. DB UNIQUE는 INSERT 순간에 원자적으로 검사하는 마지막 방어선 |
| 4 | `signup`에서 `@Transactional`을 빼면? | 모르겠음 | 클래스 기본값이 `readOnly=true`라 읽기 전용으로 실행되어 저장이 실패하거나 반영되지 않는다. **실제 동작은 직접 확인하지 못했다**(서비스 테스트에서 실험 예정) |
| 5 | `SecurityConfig`에서 `/error`를 허용하지 않으면? | 모르겠음 | 오류가 `/error`로 내부 전달될 때도 Security가 적용되어, 원래 상태 대신 401/403이 나가 원인이 가려진다 |

**복습 필요: 2~5번.**

### 알고 있는 한계 (ROADMAP과 동일)
- 동시 가입 시 `DataIntegrityViolationException`이 500으로 나감
- 인증 없는 요청이 401이 아니라 403으로 나갈 수 있음
- Spring Security의 `AccessDeniedException`도 `handleUnexpected`가 500으로 바꿀 수 있어, 마지막 항목(필터 단계 401)에서 같이 봐야 함

## 4. 결정 대기: 컨트롤러 테스트 방식

| 기준 | A. standalone MockMvc + Mockito | B. `@WebMvcTest` + `SecurityConfig` import + 서비스 목 | C. `@SpringBootTest` + MockMvc (실제 DB) |
|---|---|---|---|
| 속도 | 매우 빠름 | 빠름 | 느림(첫 컨텍스트 로딩) |
| 보안 설정(`permitAll`) 검증 | 불가 | 가능 | 가능 |
| INSERT·UNIQUE·`createdAt` 확인 | 불가 | 불가 | 가능 |
| 외부 환경 | 없음 | 없음 | MySQL (+`.env`) |
| 실패 원인 찾기 | 쉬움 | 쉬움 | 어려움 |

- **B의 핵심 가치**: `permitAll`을 지우는 보안 회귀를 테스트가 잡는다. 단점은 Boot 4에서 어노테이션·패키지가 달라 틀리기 쉽고, 서비스를 가짜로 지시하므로 "진짜 중복 판정"은 서비스 단위 테스트가 따로 필요하다는 점.
- **C의 핵심 가치**: 로드맵의 미확인 항목(INSERT)을 유일하게 해결한다. 단점은 개발 DB 오염, `@Transactional` 롤백이 `readOnly` 실험을 가리는 점, 환경 의존.
- **추천**: 서비스 단위 테스트(Mockito) + 컨트롤러 B + `signup` 통합 테스트 C 하나. 한 가지만 고르면 C.
- 아직 사용자의 선택은 없다.

### 테스트 작성 전에 확인할 것
1. `build.gradle`의 테스트 의존성은 `spring-boot-starter-test`, `spring-security-test`뿐이다. 내가 아는 Boot 4는 `@WebMvcTest`·`@AutoConfigureMockMvc`를 별도 모듈(`spring-boot-starter-webmvc-test`)로 분리했으므로 B·C는 의존성 추가가 필요할 가능성이 크다. **제 기억이라 공식 문서로 확인하고, 추가는 사람이 결정한다.**
2. `src/test/resources`가 없어 로컬 테스트가 `.env`의 개발 DB를 쓴다. 테스트 데이터 정리 방식(롤백 / 고유 이메일)을 정해야 한다.
3. `ci.yml`: MySQL 서비스는 있으나 Redis 서비스와 `JWT_SECRET`이 없다. 지금은 아무 코드도 읽지 않아 통과하지만, JWT 클래스가 `jwt.secret`을 읽거나 Redis를 실제로 쓰는 코드가 들어오면 CI가 실패한다.
4. 테스트에 `@Transactional`을 붙이면 서비스의 `@Transactional(readOnly = true)`가 테스트 트랜잭션에 합류하면서 `readOnly`가 무시된다. 4번 질문 실험은 `@Transactional` 없이 해야 한다.

## 5. 기록할 점 (AI 설명에서 바로잡은 것)

- 제가 설명에서 "요청 한 번이 시스템을 어떻게 통과하는가"라는 따옴표 표현을 마치 문서의 용어처럼 썼다. 어디에도 없는 AI 자신의 문장이었다. 문서에 근거한 말과 AI의 설명을 구분해서 읽을 것.

## 다음 할 일

- [ ] 컨트롤러 테스트 방식 결정 (A/B/C)
- [ ] (B·C 선택 시) 공식 문서로 Boot 4 테스트 어노테이션 확인 → 의존성 추가 여부 결정
- [ ] 서비스 단위 테스트: 성공, 이메일 중복, 닉네임 중복
- [ ] 컨트롤러 테스트: 검증 실패 400, 중복 409, 성공 201, `permitAll` 확인
- [ ] `signup` INSERT 확인(`createdAt`, BCrypt 해시, UNIQUE)
- [ ] 위 복습 질문 2~5번 다시 설명해 보기
- [ ] 이후: 로그인(Access Token), JWT 발급·검증, JWT 필터, `GET /api/members/me`
