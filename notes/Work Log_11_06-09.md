# 작업일지 — 2026-11-06 ~ 11-09

> 이전 기록은 `Work Log_11_05.md`, 단계별 할 일은 `docs/ROADMAP.md`, 프로젝트 방향은 `docs/DIRECTION.md`, 설명 확인 기록은 `EXPLAIN_CHECK.md` 참고.

## 지금의 위치

- 1단계(공통 기반 + 회원가입/로그인) 진행 중
- 이 기간에 끝낸 것: 설계 문서 보완(11-06), 프로젝트 방향 정의(11-08), 회원가입 테스트 3종 + 동시 가입 409 처리(11-09)
- 테스트: **19개 모두 통과** (로컬 `./gradlew test`, Docker Desktop 필요)
- 설명 확인 미해결 질문: **0개**
- 다음: `feature/signup-duplicate-409` 푸시·PR → 로그인(Access Token), JWT 필터, `GET /api/members/me`

---

## 1. 11-06: LMS·meerkatgram 참고로 설계 문서 보완

자세한 내용은 `LMS_참고_반영_내역.md`. 코드는 바꾸지 않고 문서만 바꿨다(커밋 `00dcf68`).

- 비어 있던 결정을 채움: 입력 정리 규칙, 알 수 없는 JSON 필드 거부, 페이지 규칙, Redis·S3 장애 시 503, 권한 정책 표, 새 에러 코드 6개
- ERD: 인덱스·제약 이름 규칙, 사진 키 UNIQUE, 재료 `INSERT IGNORE`, 문자셋·정렬 규칙, Flyway 도입 계획
- 테스트 방식 결정: **서비스 단위 테스트 + `@WebMvcTest` + Testcontainers 통합 테스트** (11-05의 "결정 대기" 해결)
- 범위 결정: **v1 단일 앱만 한다.** MSA, Kafka, Gateway, Kubernetes는 문서에서 제거

## 2. 11-08: 프로젝트 방향 정의 (`docs/DIRECTION.md`)

영상 「이 영상 1개로, AI시대 속 방황을 끝내드립니다」(분석좌)의 돋보기 3조건(상위목표 → 답안지 → 핵심변수)을 프로젝트에 적용했다.

### 상위목표
> 백엔드 면접에서, my-recipe의 코드와 설계 결정을 AI 없이 내 말로 설명하고 꼬리질문에 답할 수 있는 상태가 된다.

- 기존 목표 "학습 프로젝트"는 수단이었다. 그동안 범위가 여러 번 흔들린 이유가 이 상위목표가 없었기 때문이라고 진단했다.
- 기술 채택 기준: **면접 꼬리질문 3번을 버틸 수 없는 기술은 넣지 않는다.**

### 답안지 (면접 장면 10개)
- 근거는 대구·구미 채용공고 중 Java/Spring 공고 본문 11건(사람인·잡코리아)이다.
- 공고에서 확인한 것:
  - 코딩테스트는 0건이고, 모두 서류 + 면접이다.
  - SQL(JOIN·집계)은 거의 모든 공고가 요구한다.
  - MyBatis는 4건, JPA는 0건이었다.
  - 운영(로그·배포·기록)과 "기존 소스를 읽는 능력"을 요구한다.
- 장면 목록:
  - 0 프로젝트 소개
  - 1 ERD
  - 2 검색·집계 SQL
  - 3 정합성
  - 4 화면→DB 흐름
  - 5 운영 오류
  - 6 AI 코드 검증
  - 7 요구사항 정리
  - 8 실서버 배포
  - 9 인증·토큰

### 핵심변수
- 처음에는 **데이터 접근 방식**을 골랐다. 하지만 상위목표를 가장 크게 좌우하는 것은 "AI가 쓴 코드를 정말 이해하고 넘어가는가"라서, 핵심변수를 **설명 확인 통과 여부**로 바꿨다.
- 멈춤 규칙: `EXPLAIN_CHECK.md`의 미해결 질문이 **3개 이상**이면 새 작업을 멈추고 복습부터 한다.

### 이날 정한 것
| 결정 | 내용 |
|---|---|
| 데이터 접근 | **JPA + MyBatis 병행.** 쓰기·상세·일반 목록은 JPA(N+1 직접 경험), 재료 검색·인기 재료 집계는 MyBatis(읽기 전용, 3단계부터) |
| 새 기능 | 인기 재료 TOP 10 `GET /api/recipes/popular-ingredients` (GROUP BY 집계, 답안지 장면 2) |
| 8단계 추가 | EC2 1대 + docker compose로 실서버 배포. 과정은 README에 스크린샷으로 남긴다. AWS 무료 플랜 종료 전에 끝낸다 |
| 1단계 판정 | 남은 항목: 지금 11개 / 미룸 5개(Lua 원자화, 로그인 잠금, ErrorCodeSpecTest, Redis 503, 가상 스레드) / 이동 2개(batch fetch size → 2단계 N+1 해결 뒤, Redis AOF → 8단계). 미룬 항목은 ROADMAP "MVP 이후" 절로 옮김 |
| Refresh 재발급 | MVP는 단순 방식으로 하고, 동시 재발급 경쟁 상태는 "알고 있는 한계"로 문서화 |
| 작업 규칙 | 커밋 전 **설명 확인**(세 질문) 절차를 AGENTS.md·CLAUDE.md에 추가 |

- 8단계를 추가하다 발견한 것: 배포에서는 Refresh Token 쿠키가 `Secure`라 **HTTPS가 필요하다.** 방법과 비용은 8단계를 시작할 때 정한다.
- 이날의 문서 변경은 커밋 `4e0b4ac`(PR #2)로 반영했다.

## 3. 11-09: 설명 깊이 조정과 회원가입 테스트

### 설명 깊이 조정
- 어노테이션 내부 동작까지 파고드는 건 시간 낭비라서, 깊이를 **"왜 이렇게 설계했나 + 빼면 사용자·데이터에 무슨 일이 생기나"**까지로 낮췄다.
- 내부 원리는 물어볼 때만 설명한다. 단, HTTP 상태 코드 같은 기본 지식은 계속 묻는다.

### 복습 질문 결과 (전부 해결)
번호는 `Work Log_11_05.md`의 확인 질문 번호다. 1번(`@EnableJpaAuditing`)은 다시 묻지 않았지만, 통합 테스트에서 가입 시각(`createdAt`)이 채워지는 것으로 확인했다.

| # | 질문 | 답한 날 | 다시 답한 내용 |
|---|---|---|---|
| 2 | 핸들러가 여러 개 맞으면? | 11-08 | 예외 타입이 가장 구체적인 핸들러가 선택된다 |
| 3 | `exists`를 통과해도 UNIQUE 위반이 나는 경우 | 11-08 | 같은 이메일 동시 요청 → 둘 다 "없음" → 둘 다 저장 → 늦은 쪽이 UNIQUE 위반 → 500(→ 11-09에 409로 고침) |
| 4 | `readOnly`와 `@Transactional` | 11-09 | 조회는 데이터를 바꾸면 안 돼서 나눴다. 빼먹으면 클래스의 `readOnly`가 적용돼 저장이 안 된다 → **통합 테스트 실험으로 확인** |
| 5 | `/error`를 왜 허용하나 | 11-09 | 막혀 있으면 진짜 에러가 401로 가려져 원인을 알 수 없다 |
| 새 | HTTP 상태 코드 | 11-09 | 401 로그인 필요 · 403 권한 부족 · 404 찾는 자료가 없음 · 500 내부 서버 오류 (404를 여러 번 헷갈린 뒤 통과) |
| 새 | DB UNIQUE를 따로 둔 이유 | 11-09 | `exists`는 동시 요청을 막지 못해서 DB 제약을 마지막 방어선으로 둔다 (힌트를 받고도 못 답해 설명을 들은 뒤, 나중에 다시 답해 통과) |

### `SecurityConfig` 정리
- 파일 하나에 두 가지가 들어 있다: 비밀번호 저장 방식(BCrypt)과 출입 규칙(`SecurityFilterChain`).
- 꼬리질문 대비: "CSRF를 껐는데 Refresh Token은 쿠키에 있잖아요?" → `SameSite=Strict`로 막는다.

### 회원가입 테스트 3종
| 테스트 | 개수 | 확인한 것 |
|---|---|---|
| `MemberServiceTest` (단위, Mockito) | 6 | 성공 시 암호화 저장, 이메일·닉네임 중복, DB 제약 위반을 409로 변환, 모르는 제약은 그대로 던짐 |
| `MemberControllerTest` (`@WebMvcTest` + `SecurityConfig`) | 4 | 201 + 비밀번호 미노출, 400 + fieldErrors, 409, 보안 설정 적용(현재 403) |
| `MemberSignupIntegrationTest` (Testcontainers MySQL 8.4) | 4 | 실제 INSERT·암호화·가입 시각, UNIQUE 제약, `readOnly` 실험, **같은 이메일 5건 동시 가입 → 1건 성공·4건 409** |

- `contextLoads`도 개발 DB(`.env`) 대신 테스트 컨테이너를 쓰게 바꿨다. 그래서 CI의 MySQL 서비스와 `DB_*` 환경 변수를 지웠다.

### 실험·확인으로 알게 된 것
- **`readOnly` 트랜잭션에서 저장**: MySQL 드라이버가 `Connection is read-only. Queries leading to data modification are not allowed`로 거부했다.
- **MySQL 제약 이름 형식**: `for key 'member.uk_member_email'`처럼 테이블 이름을 앞에 붙인다. 그래서 이름 비교는 두 형태 모두 받는다(ERD에 기록).
- **Spring Boot 4 테스트 의존성**:
  - `@WebMvcTest`는 `spring-boot-starter-webmvc-test`로 분리됐다.
  - `spring-security-test`를 `spring-boot-starter-security-test`로 바꿨다. Boot 4 이전 도구가 권장하는 방식을 따른 판단이고, 공식 문서 문장으로는 확인하지 못했다. 컨트롤러 테스트 4번으로 보안 설정이 실제로 적용되는 것은 확인했다.
  - Testcontainers 2.x는 `testcontainers-mysql`, `org.testcontainers.mysql.MySQLContainer`를 쓴다.

### 동시 가입 409 처리 (커밋 `cbf57c9`)
- `Member`: UNIQUE 제약에 이름을 붙였다(`uk_member_email`, `uk_member_nickname`).
- `MemberService.signup`: `saveAndFlush`로 저장하고, 위반된 제약 이름으로 `EMAIL_DUPLICATED`/`NICKNAME_DUPLICATED`(409)로 바꾼다.

### Git
- 병합 완료: PR #2(11-08 문서 변경), PR #3(문서 정비 + 서비스 단위 테스트), PR #4(컨트롤러 테스트), PR #5(통합 테스트 + CI 정리).
- `feature/signup-duplicate-409`: 커밋 2개(`cbf57c9`, `b081600`). **푸시 전.**

## 4. 기록할 점 (AI 설명·작업에서 바로잡은 것)

- **핵심변수를 처음에 잘못 골랐다.**
  - 학습 방식 문제를 "가장 중요"라고 짚어 놓고, 핵심변수는 기술 선택(데이터 접근)으로 둬서 앞뒤가 맞지 않았다.
  - 영상과 대조하다 발견해서 고쳤다.
- **질문 3을 너무 깊게 냈다.** JPA의 INSERT 시점, DB 연결의 읽기 전용 같은 원리를 물었다. 이게 설명 깊이를 조정한 계기가 됐다.
- **`/error` 질문의 예시가 틀렸다.**
  - "입력 오류(400)가 `/error`로 간다"고 했다.
  - 실제로는 우리 핸들러가 직접 처리해서 `/error`를 거치지 않는다.
  - `/error`로 가는 건 핸들러가 잡지 못한 에러(필터 단계 등)다.
- **커밋을 `main`에 했다.**
  - PR 병합 뒤 `main`으로 이동한 걸 확인하지 않았다.
  - 푸시 전이라 `feature/signup-integration-test`로 옮기고 `main`을 되돌렸다.
  - 이후 커밋 전에 브랜치를 먼저 확인한다.
- **공고 본문이 처음에는 열리지 않았다.** 요약표만 보고 판단했다가, 앱 브라우저로 본문을 읽고 답안지를 보정했다(장면 7·8 추가, presigned URL 재평가).

## 5. 알고 있는 한계

- 인증 없는 요청이 401이 아니라 **403**으로 나간다. 1단계 "필터 단계 에러(401)" 항목에서 고치고, 컨트롤러 테스트 4번의 기대값도 401로 바꾼다.
- 개발 DB는 `ddl-auto: update`라서 **이름 없는 예전 UNIQUE와 이름 붙은 새 UNIQUE가 함께 있다.** 개발 서버로 중복 가입을 시험하면 500이 날 수 있다. 2단계 전 Flyway로 정리한다(테스트는 영향 없음).
- 통합 테스트에 Redis 컨테이너가 아직 없다. Refresh Token 작업을 시작할 때 `IntegrationTestSupport`에 추가한다.

## 다음 할 일

- [ ] `feature/signup-duplicate-409` 푸시·PR, CI 통과 확인
- [ ] 로그인(Access Token 발급), JWT 발급·검증 클래스, JWT 필터, `GET /api/members/me` (답안지 장면 9)
- [ ] JWT `type`·`jti` 클레임, `JwtConfig`(시크릿 32바이트 검증, `iss`)
- [ ] Refresh Token + Redis(단순 방식) + 통합 테스트 Redis 컨테이너
- [ ] 필터 단계 401 응답(`SecurityExceptionHandler`)
- [ ] 2단계 전: Flyway `V1__init.sql`, `ddl-auto: validate`
- [ ] AWS 무료 플랜 종료일 확인 → ROADMAP 8단계 머리말에 적기
- [ ] `README.md` 만들기 (답안지 장면 0, 8단계 배포 기록)
