# 코드 컨벤션

> 기준일: 2026-10-02 · 상태: 초안
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
│   ├─ repository
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

- 다른 도메인의 Repository를 직접 쓰지 않는다. 다른 도메인의 Service를 호출한다.
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
- 단순 조회는 Spring Data JPA 메서드 이름, 조인이 필요한 조회는 `@Query`(JPQL)를 쓴다.
- N+1이 생기는 목록 조회는 fetch join 또는 `@BatchSize`로 처리하고, 이유를 주석으로 남긴다.
- S3 접근은 `global/storage`의 `StorageService` 한 곳에서만 한다. 버킷 이름, 키 규칙, 공개 URL 조립을 다른 클래스에서 직접 다루지 않는다. DB에는 객체 키만 저장하고 URL은 응답 DTO를 만들 때 조립한다.
- S3 객체 삭제는 DB 트랜잭션 안에서 하지 않고, 커밋 후(`@TransactionalEventListener(phase = AFTER_COMMIT)`) 한다.
- Redis 접근은 member 도메인의 두 클래스에서만 `StringRedisTemplate`으로 한다. Refresh Token은 `RefreshTokenRepository`(`refresh:{memberId}`), 로그인 실패 횟수는 `LoginAttemptRepository`(`login-fail:{email}`). 키 이름과 TTL은 이 클래스 밖에서 직접 다루지 않는다.

**Entity**
- `BaseTimeEntity`를 상속한다(`created_at`, `updated_at`).
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, Setter는 만들지 않는다. 값 변경은 의미 있는 이름의 메서드로 한다(예: `recipe.update(title, description)`).
- 좋아요 수는 Entity 필드를 읽어서 +1 하지 않는다. 동시 요청에서 값이 꼬이므로 `UPDATE recipe SET like_count = like_count + 1` 쿼리로 DB에서 올린다.
- 연관관계는 기본 `LAZY`. 양방향 연관관계는 꼭 필요할 때만 둔다.

**DTO**
- 가능하면 Java `record`로 만든다.
- 요청 DTO에 검증 어노테이션(`@NotBlank`, `@Size`, `@Email`)을 단다.
- Entity → DTO 변환은 DTO의 정적 메서드 `from(entity)`로 한다.

### 예외 처리

- 업무 예외는 `BusinessException(ErrorCode)` 하나로 던진다. 도메인마다 예외 클래스를 늘리지 않는다.
- `ErrorCode`는 enum으로 관리하고, HTTP 상태·코드 문자열·메시지를 함께 가진다. 코드 목록과 응답 형식은 `API_SPEC.md`의 에러 코드 표와 1:1로 맞춘다.
- `@RestControllerAdvice` 전역 핸들러 하나에서 모든 예외를 공통 에러 응답으로 바꾼다.
- `catch` 후 아무것도 하지 않거나 `e.printStackTrace()`를 쓰지 않는다.

### 보안

- Spring Security의 기본 보안 헤더(`headers()`)를 끄지 않는다.
- JWT에는 `type` 클레임(`access` / `refresh`)을 넣고, 쓰는 곳에서 종류를 확인한다. 서명 알고리즘은 HS256으로 고정한다.
- 권한 확인(작성자 본인인지)은 Service에서 하고, 프론트에서 버튼을 숨긴 것을 권한 확인으로 치지 않는다.
- 에러 응답에 스택 트레이스·예외 메시지·SQL을 넣지 않는다. 원인은 로그에만 남긴다.

### 로그

- SLF4J(`@Slf4j`)를 쓴다. `System.out.println`은 쓰지 않는다.
- 비밀번호, Access Token, Refresh Token(해시 포함), Presigned URL(서명 포함), AWS 접속 키는 로그에 남기지 않는다.

### 테스트

- 테스트 클래스는 `{대상}Test`, 메서드 이름은 한글로 상황과 결과를 적는다.
  예: `같은_사용자가_좋아요를_두번_누르면_예외가_발생한다()`
- given / when / then 주석으로 구분한다.

## 2. 프론트엔드 (Vue 3 · JavaScript)

### 폴더 구조

```text
src/
├─ api/          도메인별 API 함수 (recipeApi.js, authApi.js)
├─ components/   재사용 컴포넌트
├─ views/        라우트 단위 화면 (RecipeDetailView.vue)
├─ stores/       Pinia 스토어 (useAuthStore.js)
├─ router/
└─ utils/
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
