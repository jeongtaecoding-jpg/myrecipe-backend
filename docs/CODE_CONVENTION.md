# 코드 컨벤션

> 기준일: 2026-10-02 · 상태: 초안 · 최종 수정: 2026-11-08 (JPA·MyBatis 경계)
> 2026-11-08 판정: 로그인 실패 잠금(429 `LOGIN_TOO_MANY_ATTEMPTS`, `login-fail`), Redis 장애 시 503, Refresh 재발급 Lua 원자화, `ErrorCodeSpecTest`는 **MVP 이후로 미뤘다**(ROADMAP "MVP 이후 (미룬 항목)"). 이 문서의 해당 설계는 나중에 구현할 목표로 남겨 두며, MVP에서는 구현하지 않는다.
> 읽는 때: 코드를 작성하거나 리뷰할 때

## 0. 프로젝트 기본 설정 (확정)

| 항목 | 값 |
|---|---|
| Java | 21 |
| 빌드 도구 | Gradle (Groovy DSL) |
| Spring Boot | 4.1.x (2026-10 기준 지원 중인 최신 정식 버전. 3.5는 2026-06 지원 종료) |
| 루트 패키지 | `com.myrecipe` |
| Lombok | 사용 |
| 포맷터 | IntelliJ 기본 + 아래 규칙, 프론트는 Prettier + ESLint |

Spring Boot 4는 3.x와 달라진 점이 있어(예: Jackson 3, 일부 설정 이름), 인터넷의 3.x 예제를 그대로 쓰면 안 될 수 있다. 막히면 Spring Boot 4 공식 문서와 마이그레이션 가이드를 먼저 확인한다.

## 1. 백엔드 (Java · Spring Boot)

### 패키지 구조

도메인(업무) 단위로 나누고, 업무와 무관한 기술 코드는 `global`에 둔다.

```text
com.myrecipe
├─ member / recipe / ingredient / like / comment
│   ├─ controller
│   ├─ service
│   ├─ repository  (JPA)
│   ├─ mapper      (MyBatis 조회 전용 인터페이스. SQL은 resources/mapper/{도메인}/*.xml)
│   ├─ entity
│   ├─ dto
│   └─ event       (그 모듈이 발행하는 이벤트. 예: recipe/event/RecipeDeletedEvent)
├─ facade          (여러 모듈을 모으는 응답 조립. 예: RecipeDetailFacade)
└─ global
    ├─ config      (Security, Web 설정)
    ├─ security    (JWT 발급·검증 필터, 인증 주체, Refresh Token 쿠키 생성)
    ├─ exception   (BusinessException, ErrorCode, 전역 핸들러)
    ├─ response    (페이지 응답, 에러 응답 형식)
    ├─ storage     (AWS S3 연동: Presigned URL 발급, 객체 확인·삭제)
    └─ entity      (BaseTimeEntity)
```

- 다른 도메인의 Repository·Mapper를 직접 쓰지 않는다. 다른 도메인의 Service를 호출한다. 단, 조회 전용 Mapper의 SQL은 의존 방향(recipe → ingredient) 안에서 다른 모듈의 테이블을 JOIN할 수 있다(재료 검색, 인기 재료는 recipe 모듈의 Mapper).
- 의존 방향은 한쪽으로만: like·comment → recipe → member·ingredient. **recipe는 like·comment를 import하지 않는다.** 순환이 생기면 `@Lazy`로 덮지 말고 이벤트나 facade로 푼다(아키텍처 문서 "의존 방향 규칙").
- `facade`는 도메인 Service만 호출하고, Repository·Entity를 직접 다루지 않는다. 도메인 쪽(Service·Repository·Entity)은 facade를 import하지 않는다. facade를 부르는 것은 Controller뿐이다.
- `global`은 도메인 패키지를 import하지 않는다.

### 이름 규칙

| 대상 | 규칙 | 예 |
|---|---|---|
| 클래스 | PascalCase, 역할을 접미사로 | `RecipeController`, `RecipeService`, `RecipeRepository` |
| 메서드·변수 | camelCase, 동사로 시작 | `createRecipe`, `findByIngredientName` |
| 상수 | UPPER_SNAKE_CASE | `MAX_TITLE_LENGTH` |
| 요청 DTO | `{대상}{동작}Request` | `RecipeCreateRequest`, `CommentCreateRequest` |
| 응답 DTO | `{대상}{형태}Response` | `RecipeDetailResponse`, `RecipeSummaryResponse` |
| 테이블·컬럼 | snake_case (ERD와 동일) | `recipe_ingredient`, `created_at` |

축약어는 쓰지 않는다(`rcp` ✗, `recipe` ✓).

### 계층별 규칙

**Controller**
- 요청 검증(`@Valid`)과 서비스 호출, 응답 변환만 한다. 업무 로직을 넣지 않는다.
- Entity를 그대로 반환하지 않는다. 항상 응답 DTO로 바꿔 반환한다.
- 로그인 사용자는 `@AuthenticationPrincipal`로 받는다. 요청 본문의 `memberId`를 믿지 않는다.

**Service**
- 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`을 붙인다.
- 권한 확인(작성자 본인인지)은 Service에서 한다.

**이벤트**
- 이벤트는 발행하는 모듈의 `event` 패키지에 `record`로 만든다. 이름은 과거형: `RecipeDeletedEvent(Long recipeId)`.
- 발행은 `ApplicationEventPublisher.publishEvent()`.
- DB 데이터 정리처럼 **같은 트랜잭션에서 함께 성공·실패해야 하는 일**은 `@EventListener`(동기, 같은 트랜잭션). 리스너는 해당 모듈의 Service에 둔다.
- S3 객체 삭제처럼 **되돌릴 수 없는 외부 작업**은 `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- 이벤트에는 엔티티가 아니라 ID 같은 값만 담는다.

**Repository**
- **JPA와 MyBatis의 경계 (2026-11-08 결정, `DIRECTION.md`)**
  - JPA: 쓰기(저장·수정·삭제) 전부, ID 단건 조회, 상세 조회, 일반 레시피 목록. 단순 조회는 Spring Data JPA 메서드 이름, 조인이 필요하면 `@Query`(JPQL).
  - MyBatis: 재료 검색 목록, 인기 재료 집계처럼 SQL을 직접 써야 하는 **읽기 전용** 조회. 결과는 Entity가 아니라 응답용 DTO(record)로 받는다.
  - MyBatis로는 INSERT·UPDATE·DELETE를 하지 않는다. 쓰기를 JPA로 모아 영속성 컨텍스트와 DB가 어긋나는 경로를 없앤다. (`INSERT IGNORE`, `like_count` 증감은 JPA 네이티브·`@Modifying` 쿼리로 둔다.)
  - 같은 트랜잭션에서 JPA로 쓴 직후 MyBatis로 조회하지 않는다. 꼭 필요하면 `flush()`를 명시적으로 부르고 이유를 주석으로 남긴다.
  - MyBatis 파라미터는 `#{}`만 쓴다. `${}`는 정렬 컬럼처럼 불가피할 때만, 허용 값 목록으로 검증한 뒤 쓴다(SQL Injection 방지).
  - Mapper SQL은 실행 계획(`EXPLAIN`)을 확인한 뒤 반영하고, 확인한 인덱스를 XML 주석으로 남긴다.
- N+1이 생기는 목록 조회는 fetch join 또는 `@BatchSize`로 처리하고, 이유를 주석으로 남긴다.
- S3 접근은 `global/storage`의 `StorageService` 한 곳에서만 한다. 버킷 이름, 키 규칙, 공개 URL 조립을 다른 클래스에서 직접 다루지 않는다. DB에는 객체 키만 저장하고 URL은 응답 DTO를 만들 때 조립한다.
- S3 객체 삭제는 DB 트랜잭션 안에서 하지 않고, 커밋 후(`@TransactionalEventListener(phase = AFTER_COMMIT)`) 한다.
- Redis 접근은 member 도메인의 두 클래스에서만 `StringRedisTemplate`으로 한다. Refresh Token은 `RefreshTokenRepository`(`refresh:{memberId}`), 로그인 실패 횟수는 `LoginAttemptRepository`(`login-fail:{email}`). 키 이름과 TTL은 이 클래스 밖에서 직접 다루지 않는다.

**Entity**
- `BaseTimeEntity`를 상속한다(`created_at`, `updated_at`).
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, Setter는 만들지 않는다. 값 변경은 의미 있는 이름의 메서드로 한다(예: `recipe.update(title, description)`).
- 좋아요 수는 Entity 필드를 읽어서 +1 하지 않는다. 동시 요청에서 값이 꼬이므로 `UPDATE recipe SET like_count = like_count + 1` 쿼리로 DB에서 올린다.
- 연관관계는 기본 `LAZY`. 양방향 연관관계는 꼭 필요할 때만 둔다.

**공통 작성 규칙 (2026-11-06 추가)**
- 의존성 주입은 생성자 주입만 쓴다(`@RequiredArgsConstructor`). 필드 `@Autowired` 금지.
- `@Data`, `@Setter`를 쓰지 않는다. `@ToString`은 연관관계 필드를 제외한다(지연 로딩 폭발·순환 방지). `@Builder`는 실제로 쓸 때만 단다.
- `equals/hashCode`를 직접 정의할 때는 id만 비교하되, id가 null인(저장 전) 엔티티끼리는 같지 않다고 본다. 쓰지 않으면 정의하지 않는다.
- 리포지토리의 `Optional`은 서비스에서 `orElseThrow(BusinessException)`으로 푼다. `Optional`을 필드·파라미터로 쓰지 않는다.
- boolean 이름은 `is`/`has`로 시작한다(`likedByMe` 같은 응답 필드는 예외로 API_SPEC을 따른다). 안 쓰는 코드·주석 처리된 코드는 남기지 않는다.
- 중첩 객체·리스트를 받는 요청 DTO는 필드에 `@Valid`를 붙인다. 자유 입력 문자열에는 반드시 `@Size`로 길이 상한을 둔다.
- UNIQUE 위반은 서비스에서 `saveAndFlush`로 즉시 DB에 보내고 `DataIntegrityViolationException`을 제약 이름(ERD 참고)으로 구분해 구체적 `ErrorCode`로 바꾼다. 범용 409로 뭉뚱그리지 않는다(바꿀 수 없으면 500 + error 로그).
- 재료 get-or-create는 `INSERT IGNORE`(네이티브 쿼리) 후 `findByName`으로 읽는다. 중복 위반을 잡아 재시도하지 않는다(트랜잭션이 망가진다). 자세한 이유는 ERD "저장 규칙".
- 입력 정리(앞뒤 공백 제거, 이메일 소문자)는 요청 DTO의 compact 생성자가 아니라 **Service 진입 직후** 한곳(예: `Normalizer`/DTO 메서드)에서 한다. 검증 어노테이션은 정리 전 값에 걸리므로, 공백만 있는 값은 `@NotBlank`가, 길이는 정리 뒤 서비스가 다시 확인한다. 규칙은 API_SPEC 공통 규칙을 따른다.
- 외부 호출(S3 검증 등)은 DB 트랜잭션을 열기 **전에** 한다. 같은 클래스의 `@Transactional` 메서드를 내부 호출하면 적용되지 않으므로 `TransactionTemplate`이나 별도 빈으로 나눈다.
- 서비스에서 스프링의 `AccessDeniedException`을 던지지 않는다(`BusinessException(*_FORBIDDEN)`). JSON 응답에 전역 `NON_NULL` 설정을 쓰지 않는다(필드가 사라지면 프론트가 헷갈린다).
- 외부 호출(S3, Redis)에는 타임아웃을 건다(Redis 2초, S3 클라이언트 연결·응답 타임아웃).
- CORS는 `SecurityFilterChain`의 `.cors()` 한 곳에서만 설정한다(현재는 Vite 프록시로 불필요).

**DTO**
- 가능하면 Java `record`로 만든다.
- 요청 DTO에 검증 어노테이션(`@NotBlank`, `@Size`, `@Email`)을 단다.
- Entity → DTO 변환은 DTO의 정적 메서드 `from(entity)`로 한다.

### 예외 처리

- 업무 예외는 `BusinessException(ErrorCode)` 하나로 던진다. 도메인마다 예외 클래스를 늘리지 않는다.
- `ErrorCode`는 enum으로 관리하고, HTTP 상태·코드 문자열·메시지를 함께 가진다. 코드 목록과 응답 형식은 `API_SPEC.md`의 에러 코드 표와 1:1로 맞춘다.
- `@RestControllerAdvice` 전역 핸들러 하나에서 모든 예외를 공통 에러 응답으로 바꾼다.
- `GlobalExceptionHandler`는 `ResponseEntityExceptionHandler`를 상속하고 `handleExceptionInternal`도 재정의해, 스프링 내부 예외(404·405·415·형식 오류)도 `ErrorResponse`로 응답한다. `ConstraintViolationException`은 400 `INVALID_INPUT`.
- 시큐리티 단계 오류(`AuthenticationEntryPoint`, `AccessDeniedHandler`)와 JWT 필터의 예외는 `HandlerExceptionResolver`에 위임해 같은 핸들러에서 응답한다(`SecurityExceptionHandler`). 인증 실패 401, 권한 부족 403 `FORBIDDEN`.
- 로그인 사용자는 `AuthMember(Long memberId)` record를 principal로 쓴다.
- Redis 장애(`RedisConnectionFailureException` 등)는 503 `SERVICE_UNAVAILABLE`로 바꾼다. Redis를 쓰는 로그인·재발급·로그아웃만 영향받는다.
- 요청 JSON에 정의되지 않은 필드가 있으면 거부한다(`fail-on-unknown-properties`; Boot 4/Jackson 3 설정 이름은 테스트로 확인).
- `catch` 후 아무것도 하지 않거나 `e.printStackTrace()`를 쓰지 않는다.

### 보안

- Spring Security의 기본 보안 헤더(`headers()`)를 끄지 않는다.
- JWT에는 `type` 클레임(`access` / `refresh`)을 넣고, 쓰는 곳에서 종류를 확인한다. 서명 알고리즘은 HS256으로 고정한다.
- 권한 확인(작성자 본인인지)은 Service에서 하고, 프론트에서 버튼을 숨긴 것을 권한 확인으로 치지 않는다.
- 에러 응답에 스택 트레이스·예외 메시지·SQL을 넣지 않는다. 원인은 로그에만 남긴다.

### 로그

- SLF4J(`@Slf4j`)를 쓴다. `System.out.println`은 쓰지 않는다.
- 4xx 응답은 debug/warn, 5xx는 error(스택 트레이스 포함)로 남긴다.
- 요청마다 `RequestIdFilter`가 `requestId`를 MDC에 넣고(응답 `X-Request-Id`), `finally`에서 반드시 지운다. 로그 패턴에 `%X{requestId}`를 포함한다.
- 비밀번호, Access Token, Refresh Token(해시 포함), Presigned URL(서명 포함), AWS 접속 키는 로그에 남기지 않는다.

### 테스트

- 테스트 클래스는 `{대상}Test`, 메서드 이름은 한글로 상황과 결과를 적는다.
  예: `같은_사용자가_좋아요를_두번_누르면_예외가_발생한다()`
- given / when / then 주석으로 구분한다.
- 종류: ① 서비스 단위 테스트(Mockito) ② 컨트롤러 테스트 `@WebMvcTest` + `SecurityConfig` import(검증·상태 코드·보안 설정) ③ 통합 테스트 `@SpringBootTest` + Testcontainers(MySQL 8.4, Redis). 통합 테스트는 `src/test/.../support`의 추상 클래스 `IntegrationTestSupport`를 상속하고, 컨테이너는 static으로 한 번만 띄워 공유한다(`@DynamicPropertySource`).
- 테스트는 개발 DB·`.env`를 쓰지 않는다. 테스트용 JWT 비밀 키는 테스트가 주입하고, Redis는 `@BeforeEach`에서 비운다. 흐름 테스트에는 `@Transactional`을 붙이지 않는다(`readOnly`·커밋 시점 동작이 가려진다).
- `ErrorCodeSpecTest`: `API_SPEC.md` 6장 표의 코드·상태와 `ErrorCode` enum이 1:1인지 비교한다(Swagger 예시도 이 enum에서 만든다).
- 목록 API는 쿼리 수 테스트(Hibernate statistics)로 N+1이 없음을 확인한다(QA3).

### Swagger 어노테이션

- 컨트롤러 클래스에 `@Tag`, 메서드에 `@Operation(summary)`, 던질 수 있는 에러는 `@ApiErrorCodes({ErrorCode...})`로 나열한다(`global/config/openapi`의 커스텀 어노테이션 + `OperationCustomizer`). 성공 응답은 직접 쓴다.
- 요청 DTO 필드에 `@Schema(description, example)`. 교차 검증용 `@AssertTrue` 메서드에는 `@Schema(hidden = true)`.
- Swagger UI는 `local` 프로필에서만 켠다.

### 편집기 설정 (.editorconfig)

저장소 루트에 둔다. 들여쓰기는 Java 등 일반 파일 4칸, `html/css/js/vue/json/yaml` 2칸, `charset = utf-8`, 마지막 줄 개행, **줄바꿈은 `lf`**(Docker·CI에서 `gradlew`, 셸 스크립트가 CRLF면 깨진다. `.gitattributes`의 `gradlew text eol=lf`와 같은 방향). 프론트 저장소에도 같은 파일을 둔다.

## 2. 프론트엔드 (Vue 3 · JavaScript)

### 폴더 구조

```text
src/
├─ api/          도메인별 API 함수 (recipeApi.js, authApi.js)
├─ components/   재사용 컴포넌트
├─ views/        라우트 단위 화면 (RecipeDetailView.vue)
├─ stores/       Pinia 스토어 (useAuthStore.js)
├─ router/
├─ utils/
│   └─ validators/   rules.js(필드별 규칙), 화면별 묶음(signupValidator.js 등)
```

### 이름 규칙

| 대상 | 규칙 | 예 |
|---|---|---|
| 컴포넌트 파일 | PascalCase, 두 단어 이상 | `RecipeCard.vue`, `CommentList.vue` |
| 화면 파일 | `{이름}View.vue` | `RecipeSearchView.vue` |
| Pinia 스토어 | `use{이름}Store` | `useAuthStore` |
| 이벤트 이름 | kebab-case | `@delete-comment` |

### 작성 규칙

- `<script setup>` + Composition API를 쓴다.
- 사용자가 입력한 글은 `{{ }}`로만 출력한다. **`v-html`은 쓰지 않는다**(XSS).
- 토큰을 `localStorage`·`sessionStorage`에 저장하지 않는다.
- API 호출은 `api/` 폴더의 함수로만 한다. 컴포넌트에서 axios를 직접 부르지 않는다.
- JWT 첨부와 401 처리는 axios 인터셉터 한 곳에서 한다.
- 입력 검증 규칙은 `utils/validators/rules.js`의 순수 함수로 둔다. 통과하면 빈 문자열 `''`, 실패하면 오류 메시지를 반환하고, 화면별 파일이 필요한 규칙만 묶는다. 서버 제한(이메일 100자, 비밀번호 8~20자, 닉네임 2~30자, 제목 1~100자 등)과 같은 값을 쓰고, 최종 검증은 서버가 한다. 닉네임은 한글을 허용한다.
- 라우트는 `meta: { requiresAuth, guestOnly }`로 표시하고, `router.beforeEach` 한 곳에서 처리한다. 로그인이 필요한 화면에 비로그인으로 오면 `/login?redirect=원래경로`로 보내고, 로그인한 사용자가 `/login`·`/signup`에 오면 홈으로 보낸다.
- 앱 시작 시 세션 복구(재발급)는 **한 번만** 한다(`main.js`에서 라우터 시작 전에 await). 가드가 이동할 때마다 재발급을 시도하지 않는다. 재발급 실패는 비로그인 상태로 두고(오류 화면으로 보내지 않음) 조용히 넘어간다.
- 스토어에서 API 오류를 삼켜 `null`을 반환하지 않는다. 호출한 쪽이 처리하도록 던진다(업로드 실패 등).
- 예상 못 한 오류(500, 네트워크)는 공통 에러 상태 스토어에 담아 `/error` 화면에 표시한다. 폼 오류(400)는 칸 아래에 표시한다.

## 3. Git

### 브랜치

- `main`: 동작하는 상태
- `feature/{이슈번호}-{짧은-설명}`: 기능 작업 (예: `feature/3-recipe-crud`)

### 커밋 메시지

```text
{타입}: {요약}

{필요하면 본문: 무엇을, 왜}
```

| 타입 | 의미 |
|---|---|
| feat | 기능 추가 |
| fix | 버그 수정 |
| refactor | 동작 변화 없는 구조 개선 |
| test | 테스트 추가·수정 |
| docs | 문서 |
| chore | 빌드·설정 |

예: `feat: 재료 이름으로 레시피 검색 API 추가`
