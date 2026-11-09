# 구현 로드맵

> 기준일: 2026-10-02 · 최종 수정: 2026-11-08 (MyBatis·인기 재료, 설명 확인 절차)
> 한 단계씩 끝내고 다음으로 넘어간다. 단계마다 새로 배우는 개념이 하나씩 늘어나도록 나눴다.

각 단계의 완료 기준: **관련 테스트 통과 + `INTEGRATION.md` 체크리스트의 해당 항목 확인 + 아래 "기록"에 한두 줄 남기기**

## 0단계. 시작 준비

- [x] Spring Initializr로 `myrecipe_backend/` 프로젝트 생성 (설정은 아래)
- [x] `myrecipe_backend/src/main/resources/application.yaml` 내용을 환경 변수를 읽는 설정으로 교체
- [x] `myrecipe_backend`에서 `.env.example`을 복사해 `.env` 만들고 비밀번호 바꾸기
- [x] `myrecipe_backend`에서 `docker compose up -d` → `docker compose ps -a`로 mysql(healthy)·redis 실행 확인
- [x] `myrecipe_backend`에서 `./gradlew bootRun` → 오류 없이 8080에서 실행
- [x] `myrecipe_backend`를 Git 저장소로 만들고 GitHub에 올리기 (`.env`가 올라가지 않았는지 확인) → GitHub Actions 초록불

Spring Initializr(https://start.spring.io) 설정:

| 항목 | 값 |
|---|---|
| Project | Gradle - Groovy |
| Language | Java |
| Spring Boot | 4.1.x 중 최신 정식 버전 (SNAPSHOT·M·RC 제외) |
| Group / Artifact / Package name | `com` / `myrecipe` / `com.myrecipe` |
| Packaging | Jar (내장 Tomcat, `java -jar`로 실행. Docker 이미지도 이 방식 전제) |
| Configuration | YAML (`application.yaml` 생성) |
| Java | 21 |
| Dependencies | Spring Web, Spring Data JPA, MySQL Driver, Spring Security, Validation, Spring Data Redis (Access+Driver), Lombok |

압축을 풀면 `myrecipe/` 폴더가 나온다. 이 폴더 이름을 `myrecipe_backend`로 바꿔 `my-recipe-v1/` 바로 아래에 둔다.

나머지 라이브러리는 필요한 단계에서 직접 추가한다: JWT(1단계, jjwt 0.13.0으로 결정), MyBatis(3단계, `mybatis-spring-boot-starter` 4.1.x — Boot 4.1 대응 버전을 공식 문서로 확인 후), AWS SDK S3(5단계), springdoc-openapi(6단계).

각 단계의 코드는 커밋 전에 **설명 확인**(AGENTS.md 4장 6번)을 거친다. 못 답한 질문은 `notes/EXPLAIN_CHECK.md`에 남는다. **미해결 질문이 3개 이상이면 다음 체크 항목으로 넘어가지 않고 복습부터 한다**(`DIRECTION.md` 3장 핵심변수).

## 1단계. 공통 기반 + 회원가입/로그인

> 2026-11-08 판정: 남은 항목을 답안지 장면(`DIRECTION.md`) 기준으로 "지금 / 미룸"으로 나눴다. 미룬 항목은 아래 "MVP 이후 (미룬 항목)"에 이유와 함께 있다.

- [x] `global`: BaseTimeEntity, ErrorCode, BusinessException, 전역 예외 처리, 에러 응답 형식 (`GlobalExceptionHandlerTest` 4개 통과)
- [ ] 회원가입(BCrypt), 로그인(Access Token만), JWT 필터, `/api/members/me`
  - [x] `Member` 엔티티, `MemberRepository`(`existsByEmail`, `existsByNickname`, `findByEmail`)
  - [x] `SignupRequest`, `SignupResponse` (`member/dto`)
  - [x] `SecurityConfig`: `PasswordEncoder`(BCrypt), stateless, 가입·`/api/auth/**`·`/error` 허용
  - [x] `MemberService.signup` (중복 확인 → 인코딩 → 저장)
  - [x] `MemberController` `POST /api/members/signup` → 201 (작성·`compileJava` 통과. 실행·테스트는 아직 안 함)
  - [ ] 회원가입 테스트: ~~서비스 단위 테스트(성공, 이메일·닉네임 중복)~~ ✅ 2026-11-09 `MemberServiceTest` 3개 통과·설명 확인 통과 / 남은 것: 컨트롤러 테스트(검증 실패 400, 중복 409, 성공 201). `signup`의 INSERT는 아직 테스트로 확인하지 못했다 (**다음 할 일**, 컨트롤러 테스트 방식은 아래 메모의 결정 완료 항목 참고: 단위 + `@WebMvcTest` + Testcontainers)
  - [ ] 로그인(Access Token 발급), JWT 발급·검증 클래스, JWT 필터, `GET /api/members/me`
- [ ] JWT `type` 클레임(access/refresh) 확인, Refresh Token `jti` 클레임(같은 초 재발급 시 토큰 중복 방지), JWT 필터가 `/api/auth/**`는 건너뜀, `/error` 허용
- [ ] Refresh Token + Redis: 재발급(로테이션), 로그아웃, 재사용 감지. 먼저 단순한 "조회 → 비교 → 교체" 방식으로 만든다. 같은 토큰으로 동시에 온 재발급 요청이 둘 다 성공할 수 있는 경쟁 상태는 **알고 있는 한계**로 아키텍처 문서에 남긴다(Lua 원자화는 MVP 이후)
- [ ] 필터 단계 에러(401)도 API_SPEC 형식으로 응답 (`SecurityExceptionHandler` → `HandlerExceptionResolver` 위임, `AuthMember` principal)
- [ ] `JwtConfig` record(secret 32바이트 이상 검증, `iss` 확인)
- [ ] `RequestIdFilter`(MDC `requestId`, `X-Request-Id`), 로그 레벨(4xx warn/5xx error)

**문서 변경에 따른 코드 후속 작업 (2026-11-06, LMS 참고 반영)** — 아직 코드는 바뀌지 않았다.
- [ ] `ErrorCode`: `FORBIDDEN`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `SERVICE_UNAVAILABLE`, `IMAGE_ALREADY_USED` 추가 (API_SPEC 6장과 1:1)
- [ ] `GlobalExceptionHandler`: `handleExceptionInternal` 재정의, `AuthenticationException`/`AccessDeniedException`/`ConstraintViolationException` 처리, unknown 필드 거부 설정
- [ ] `Member`: UNIQUE에 이름(`uk_member_email`, `uk_member_nickname`), `MemberService.signup`은 `saveAndFlush` + 제약 이름으로 중복 변환
- [ ] `build.gradle`: `spring-boot-starter-webmvc-test`, Testcontainers(BOM, mysql) — Boot 4 호환 이름은 공식 문서로 확인 후
- [ ] `application.yaml`: Redis 타임아웃 2초. (`default_batch_fetch_size`는 2단계에서 N+1을 fetch join으로 먼저 해결한 뒤, `application-prod.yaml`은 6단계, 가상 스레드는 MVP 이후)
- [ ] `docker-compose.yml`: Redis 볼륨·healthcheck. (AOF·`noeviction`은 8단계)
- [ ] `ci.yml`: 테스트가 Testcontainers로 바뀌면 MySQL 서비스·`DB_*` 환경 변수 제거
- [ ] 2단계 전: Flyway 도입(`V1__init.sql`), `ddl-auto: validate`. 이후 기능은 feature 브랜치 → PR → CI 통과 → merge

**1단계 메모 (2026-11-03 기준)**
- 작업 방식: Claude가 코드를 쓰고 자세히 설명하며, 직접 쓰겠다고 한 부분만 힌트·리뷰로 돕는다(`CLAUDE.md`).
- 정해 둔 것: 서비스 클래스는 `@Transactional(readOnly = true)`, 쓰기 메서드만 `@Transactional`. 요청 DTO 이름은 `API_SPEC`·`openapi.yaml`을 따라 `SignupRequest`로 유지.
- 알고 있는 한계: ① 동시 가입 시 `exists` 확인을 둘 다 통과하면 DB UNIQUE 위반(`DataIntegrityViolationException`)이 500으로 나간다. (결정: `saveAndFlush` + 제약 이름으로 `EMAIL_DUPLICATED`/`NICKNAME_DUPLICATED` 변환 — 아래 후속 작업) ② 인증 없는 요청은 지금 401이 아니라 403일 수 있다. 마지막 항목 "필터 단계 에러(401)"에서 `AuthenticationEntryPoint`로 맞춘다.
- **결정 완료 (2026-11-06): 컨트롤러 테스트 방식 = 서비스 단위 테스트 + B + Testcontainers 통합 테스트(C). 근거와 상세는 `CODE_CONVENTION.md` 테스트 절. (이전 비교 메모↓)** ~~결정 대기 (2026-11-05): 컨트롤러 테스트 방식.~~ A) standalone MockMvc + Mockito(빠름, 보안 설정 검증 불가) / B) `@WebMvcTest` + `SecurityConfig` import + 서비스 목(`permitAll` 같은 보안 회귀 검증, INSERT는 못 봄) / C) `@SpringBootTest` + MockMvc(실제 DB로 INSERT·UNIQUE·`createdAt` 확인, 느리고 개발 DB 오염 주의). 추천은 서비스 단위 테스트 + B + C 하나. 상세 비교는 `notes/Work Log_11_05.md`.
- 테스트를 쓰기 전 확인할 것: ① B·C는 `build.gradle`에 `spring-boot-starter-webmvc-test` 같은 테스트 의존성이 필요할 수 있다(Boot 4 모듈 구성, 공식 문서로 확인 후 추가 여부를 사람이 결정). ② 테스트 전용 설정이 없어 로컬 테스트가 `.env`의 개발 DB를 쓴다. ③ CI에는 Redis 서비스와 `JWT_SECRET`이 없다. 지금은 아무 코드도 읽지 않아 통과하지만, JWT 클래스나 Redis 사용 코드가 들어가면 CI를 고쳐야 한다. ④ 테스트에 `@Transactional`을 붙이면 서비스의 `readOnly`가 무시되어 "읽기 전용에서 저장" 실험이 재현되지 않는다.
- 이어서 할 때: `.env`가 있어야 테스트가 통과한다(`.env.example`을 복사). `docker compose up -d`로 MySQL·Redis를 먼저 띄우고 `./gradlew test`로 시작 상태를 확인한다.

## 2단계. 레시피 CRUD (사진 없이)

- [ ] 레시피·조리 순서·재료 엔티티와 연관관계
- [ ] 시작 전: Flyway `V1__init.sql`, `.editorconfig`, local 전용 시드 SQL (`INTEGRATION.md` "운영(prod) 프로필 값과 시드 데이터")
- [ ] 등록·상세·수정(목록 통째 교체)·삭제, 작성자 권한 확인
- [ ] 입력 정리(앞뒤 공백, 이메일 소문자) 공통 처리, 재료 `INSERT IGNORE` + 조회 (ERD "저장 규칙")
- [ ] 사진 키는 이 단계에서 `null`만 허용한다(요청에 값이 있으면 `IMAGE_NOT_FOUND`). 키 검증과 함께 5단계에서 연다
- [ ] 상세·등록·수정 응답에 `thumbnailKey`, `steps[].imageKey` 포함 (항상 null)
- [ ] 삭제 시 `RecipeDeletedEvent` 발행 (리스너는 4단계에서 붙음)
- [ ] `facade/RecipeDetailFacade`로 상세 응답 조립 (이 단계에서는 `likedByMe=false`, `commentCount=0`)
- [ ] 목록 조회에서 N+1을 직접 확인하고 fetch join으로 해결
- [ ] 그다음 `default_batch_fetch_size: 100`을 켜 보고, fetch join과 무엇이 다른지 비교해 기록 (먼저 켜 두면 N+1을 겪을 기회가 사라진다)

## 3단계. 재료로 검색 + MyBatis

- [ ] `build.gradle`에 MyBatis starter 추가, `resources/mapper/` 위치·카멜케이스 매핑 설정, Testcontainers 통합 테스트에서 Mapper 동작 확인
- [ ] `?ingredient=두부` 검색, 페이징, 최신순 — **MyBatis Mapper로 SQL을 직접 작성** (재료 없는 일반 목록은 2단계 JPA 그대로)
- [ ] 검색 SQL의 실행 계획(`EXPLAIN`)으로 `(ingredient_id, recipe_id)` 인덱스 사용 확인
- [ ] 인기 재료 TOP 10 `GET /api/recipes/popular-ingredients` (MyBatis, GROUP BY) + 실행 계획 확인
- [ ] 시드 데이터로 검색 count 쿼리 시간 확인 (느리면 `hasNext`만 내리는 방식 검토)

## 4단계. 좋아요·댓글

- [ ] 좋아요 추가·취소, `like_count` 원자적 증감
- [ ] 동시에 여러 요청을 보내는 테스트로 카운트 정합성 확인
- [ ] 댓글 작성·삭제·목록
- [ ] like·comment에 `RecipeDeletedEvent` 리스너 추가 → 좋아요·댓글이 있는 레시피 삭제 테스트
- [ ] `RecipeDetailFacade`에 `likedByMe`, `commentCount` 연결
- [ ] recipe 패키지에 like·comment import가 없는지 확인

## 5단계. AWS S3 사진 업로드

- [ ] AWS 계정 보안 먼저: root MFA, 관리자 사용자(MFA), 예산 알림, GitHub Push protection (`INTEGRATION.md` 4장)
- [ ] S3 버킷(서울, 퍼블릭 액세스 차단 유지) + CORS, CloudFront(OAC) + 버킷 정책, 앱 전용 IAM 사용자 + 키 → `.env`의 `STORAGE_*` 채우기 (`infra/aws/` 정책 파일 사용)
- [ ] 테스트·CI에서는 `StorageService`를 가짜 구현으로 바꿔 AWS에 접속하지 않게 하기

- [ ] Presigned URL 발급, 레시피 저장 시 객체 확인(키 소유·존재·크기·형식·파일 앞부분 바이트), 키 중복 사용 거부(`IMAGE_ALREADY_USED`: 요청 안 중복 + 다른 레시피 사용 + 두 컬럼 UNIQUE), 수정 때 기존 키는 건너뜀
- [ ] S3 클라이언트 타임아웃(연결 2초·시도 5초·전체 10초), 타임아웃/장애 시 503
- [ ] 레시피 삭제·사진 교체 시 커밋 후 객체 삭제

## 6단계. Docker 전체 모드 + Swagger

- [ ] `Dockerfile`, `.dockerignore` (저장소 루트)
- [ ] `docker compose --profile app up -d --build`로 전체 실행
- [ ] springdoc으로 Swagger UI 생성(local만), `@ApiErrorCodes` + `OperationCustomizer`로 에러 예시 자동 생성, `openapi.yaml`과 비교
- [ ] `application-prod.yaml` 확정 (`INTEGRATION.md` 표), 컨테이너 로그는 stdout
- [ ] CI에 Docker 이미지 빌드 추가

## 7단계. Vue 화면

- [ ] 프론트 저장소 시작: `.editorconfig`, `utils/validators`(규칙 함수), 라우트 `meta` 가드, 앱 시작 시 재발급 한 번 (`CODE_CONVENTION.md` 2장)
- [ ] 로그인 유지(앱 시작 시 재발급), 자동 재발급 인터셉터, Web Locks로 탭 사이 재발급 한 번씩
- [ ] 목록·검색·상세·작성·수정 화면, 사진 업로드
- [ ] 검색창 아래 인기 재료 버튼 (누르면 그 재료로 검색)
- [ ] `INTEGRATION.md` 체크리스트 전체 통과

## 8단계. 실서버 배포 (EC2)

> 2026-11-08 추가. 답안지 8번 장면(`DIRECTION.md`). AWS 무료 플랜 종료일 전에 끝낸다. 종료일(결제 콘솔 Credits 페이지에서 확인): ____

- [ ] 시작 전: 무료 플랜 종료일 확인, 5단계 예산 알림이 켜져 있는지 확인
- [ ] **HTTPS 방법 결정**: 배포에서는 Refresh Token 쿠키가 `Secure`(`COOKIE_SECURE=true`)라 http로는 쿠키가 저장되지 않는다. 도메인 + 인증서 등 방법과 비용을 이 단계 시작 때 정한다
- [ ] EC2 1대(메모리 2GB급, 서울 리전). 보안 그룹은 SSH(내 IP만)·HTTP·HTTPS만 연다. 키 페어는 저장소에 올리지 않는다
- [ ] 서버에 Docker 설치 → 6단계 docker compose(Spring Boot + MySQL + Redis)로 실행. 서버의 `.env`는 서버에서 직접 만들고 `prod` 프로필로 실행
- [ ] 비용 때문에 RDS·NAT 게이트웨이·로드밸런서는 쓰지 않는다
- [ ] Redis AOF·`noeviction` 설정 (서버를 재시작해도 Refresh Token이 남도록, 아키텍처 "Redis 운영 설정")
- [ ] 7단계 프론트 빌드 결과를 nginx로 서빙, `/api/`는 백엔드로 프록시(아키텍처 6장)
- [ ] 동작 확인: 회원가입 → 로그인 → 사진 포함 레시피 등록 → 재료 검색 → 인기 재료 → 새로고침 후 로그인 유지
- [ ] `README.md`에 배포 과정 기록: 구성도, 단계별 명령, 확인 결과 **스크린샷**(`docs/images/deploy/`). 겪은 문제는 `notes/TROUBLESHOOTING.md`
- [ ] 다 쓰면 인스턴스를 종료(terminate)한다. 서버를 내려도 README 기록은 배포 경험의 증거로 남는다

## MVP 이후 (미룬 항목)

> 2026-11-08 판정. 설계는 각 문서에 남아 있다. MVP(1~8단계)가 끝난 뒤 다시 정한다.

| 항목 | 미룬 이유 | 설계 위치 |
|---|---|---|
| Refresh 재발급 Lua 원자화(`rotate`) | 꼬리질문이 깊다(Redis 원자성, MULTI/WATCH와의 차이). MVP는 단순 방식 + 한계를 문서화 | 아키텍처 "재발급은 원자적으로" |
| 로그인 실패 5회 → 15분 잠금, 없는 이메일도 BCrypt 비교 | 신입 면접에서 나올 확률이 낮고, 응답 시간 맞추기까지 설명할 양이 많다 | API_SPEC 로그인, INTEGRATION, ERD `login-fail` |
| `ErrorCodeSpecTest` | 설명 부담에 비해 면접 효과가 작다. 표와 enum은 리뷰로 맞춘다 | API_SPEC 머리말, CODE_CONVENTION 테스트 |
| Redis 장애 시 503 | 우선순위가 낮다 (S3 장애 503은 5단계에서 그대로 한다) | API_SPEC "장애 시 동작", QA10 |
| 가상 스레드(`spring.threads.virtual.enabled`) | 연결되는 면접 장면이 없다 | — |

## 기록

| 날짜 | 단계 | 어려웠던 점 / 새로 이해한 것 |
|---|---|---|
| 2026-11-05 | 1단계 (진행 중) | `MemberController` 작성(`@Valid` → 서비스 → `SignupResponse` → 201). 지금까지 코드를 순서대로 복습: `@EnableJpaAuditing`이 꺼지면 `@CreatedDate`가 안 채워져 `save`에서 NOT NULL 오류, 핸들러는 구체적인 예외 타입이 우선이라 `ResponseEntityExceptionHandler` 상속이 필요, `exists` 확인과 INSERT 사이 틈은 DB UNIQUE만 막음. 2~5번 질문(핸들러 우선순위·UNIQUE·`readOnly` 저장·`/error` 허용)은 아직 설명할 수 없어 복습 필요 |
| 2026-11-03 | 1단계 (진행 중) | `.env`가 없으면 `DB_URL`이 비어 `contextLoads`가 실패한다는 것을 원인 추적으로 확인. 쿼리 메서드는 `existsBy`/`findBy` 규칙과 필드명이 정확해야 하고, `boolean`(있는지)과 `Optional`(객체가 필요)을 구분한다. `@Transactional(readOnly = true)`를 클래스 기본값으로 두고 쓰기 메서드만 덮어쓴다 |
| 2026-10-08 | 0단계 | MinIO 배포 중단 → 사진 저장소를 AWS S3로 변경. PC MySQL(3306)·로컬 Kubernetes(8080) 포트 충돌을 원인 추적으로 해결. 오픈소스는 "누가 관리하는가"도 본다 |
