# API 명세 (MVP)

> 기준일: 2026-10-02 · 상태: 초안 (구현 전) · 최종 수정: 2026-11-08 (인기 재료 API)
> 2026-11-08 판정: 로그인 실패 잠금(429 `LOGIN_TOO_MANY_ATTEMPTS`, `login-fail`), Redis 장애 시 503, Refresh 재발급 Lua 원자화, `ErrorCodeSpecTest`는 **MVP 이후로 미뤘다**(ROADMAP "MVP 이후 (미룬 항목)"). 이 문서의 해당 설계는 나중에 구현할 목표로 남겨 두며, MVP에서는 구현하지 않는다.
> 읽는 때: API를 추가·변경하거나 프론트에서 호출할 때

## Swagger(OpenAPI) 문서

- 이 명세를 OpenAPI 3.0 형식으로 옮긴 파일이 `openapi.yaml`이다. [Swagger Editor](https://editor.swagger.io)에 붙여넣거나 VS Code의 OpenAPI 확장으로 열면 Swagger UI 화면으로 볼 수 있다.
- API를 바꾸면 이 문서와 `openapi.yaml`을 같은 커밋에서 함께 고친다.
- 구현을 시작하면 springdoc-openapi를 추가해 코드에서 Swagger UI를 자동 생성하고(`/swagger-ui`), 이 파일과 비교해 설계와 구현이 어긋난 곳을 찾는다. Swagger UI는 로컬 개발 환경(`local` 프로필)에서만 열고 `prod`에서는 끈다.
- **에러 응답 예시는 코드에서 자동으로 만든다**(msa4-meerkatgram 방식). 컨트롤러 메서드에 `@ApiErrorCodes({ErrorCode.RECIPE_NOT_FOUND, ...})`처럼 그 API가 던질 수 있는 `ErrorCode`만 나열하면, `OperationCustomizer`가 상태 코드별로 `{status, code, message, fieldErrors}` 예시를 만들어 Swagger에 붙인다. 예시가 enum에서 나오므로 실제 응답과 어긋나지 않는다. 요청 DTO 필드에는 `@Schema(description, example)`를 단다.
- **표와 enum의 일치는 테스트로 지킨다.** 6장 에러 코드 표를 읽어 `ErrorCode` enum의 코드·상태와 비교하는 테스트(`ErrorCodeSpecTest`)를 둔다. 표만 고치거나 enum만 고치면 테스트가 실패한다.

## 0. 공통 규칙

### 기본

- Base URL: `/api`
- 요청·응답 본문: JSON (`Content-Type: application/json`). 사진 파일은 이 API로 보내지 않고 S3에 직접 올린다([3. 이미지](#3-이미지))
- 날짜·시간: ISO 8601 (`2026-10-02T11:07:00`)
- JSON 필드 이름: camelCase
- **입력값 정리(서버가 저장·비교 전에 한다)**
  - 앞뒤 공백을 지운다: `email`, `nickname`, `title`, `ingredients[].name`, `ingredients[].amount`, `description`, `steps[].content`, `comments.content`, 검색어. 공백만 있는 값은 빈 값으로 보고 필수 필드면 `INVALID_INPUT`. 길이 규칙은 **지운 뒤 길이**로 판단한다. `password`는 지우지 않는다.
  - `email`은 소문자로 바꿔 저장·비교한다(로그인 실패 횟수 키 `login-fail:{email}`도 소문자). 가입 때와 로그인 때 같은 규칙을 쓴다.
  - 선택 필드(`description`, `amount`, `thumbnailKey`, `imageKey`)에 빈 문자열이 오면 `null`로 저장한다.
- **정의되지 않은 필드는 거부한다.** 요청 본문에 명세에 없는 필드가 있으면 `400 INVALID_INPUT`. 수정 API(`PUT`)는 전체 교체라서, 필드 이름 오타(`thumbnailkey`)가 조용히 무시되면 기존 값이 지워지기 때문이다.
- 모든 응답에 `X-Request-Id` 헤더가 붙는다(서버 로그의 `requestId`와 같은 값). 문제 문의 시 이 값을 알려 준다. 요청에 같은 헤더를 보내면 그 값을 쓰고, 없으면 서버가 만든다.

### 인증

- 로그인하면 토큰 두 개를 받는다 (유효 시간은 설계 기본값).

| 토큰 | 유효 시간 | 전달 방식 | 용도 |
|---|---|---|---|
| Access Token (JWT) | 30분 | 응답 본문 `accessToken` | API 호출 시 `Authorization: Bearer {accessToken}` 헤더로 보냄 |
| Refresh Token (JWT) | 14일 | `Set-Cookie: refresh_token=...; HttpOnly; SameSite=Strict; Path=/api/auth` (배포 시 `Secure` 추가) | Access Token 재발급. 브라우저가 `/api/auth/**` 요청에만 자동으로 보냄 |

- Access Token이 만료되면 `POST /api/auth/refresh`로 새 토큰을 받는다. Refresh Token은 서버의 Redis에 저장되어 로그아웃·재사용 감지에 쓰인다(아키텍처 문서 "Redis를 쓰는 이유").
- 아래 표의 **인증** 열: `필수` = 토큰 없으면 401, `선택` = 없어도 되지만 있으면 내 정보(예: 내가 좋아요 했는지)가 응답에 반영됨, `-` = 필요 없음, `쿠키` = Access Token 대신 `refresh_token` 쿠키를 사용
- 토큰을 보냈는데 만료·위조된 경우에는 인증 `선택` API여도 401(`TOKEN_EXPIRED`, `TOKEN_INVALID`)로 응답한다. 프론트 처리 방식은 `INTEGRATION.md`를 따른다.

### 성공 응답

- 단건 조회·생성 결과는 데이터를 그대로 반환한다.
- 목록은 아래 페이지 형식으로 반환한다. 페이지 번호는 0부터, `size` 기본 20·최대 50.
  - `size`가 50을 넘으면 50으로 줄여서 처리한다(에러 아님).
  - `page`가 0 미만이거나 `size`가 1 미만이거나 숫자가 아니면 `400 INVALID_INPUT`.
  - `totalElements`/`totalPages`는 매 요청마다 count 쿼리를 한 번 더 실행한다. MVP는 유지하되, 재료 검색(조인)의 count가 느려지면 `hasNext`만 내리는 방식(`Slice`, size+1건 조회)으로 바꾸는 것을 검토한다(3단계에서 측정).
  - 정렬은 항상 `createdAt DESC, id DESC`(같은 시각이어도 순서가 흔들리지 않게 id를 함께 쓴다).

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 135,
  "totalPages": 7,
  "hasNext": true
}
```

| 상황 | 상태 코드 |
|---|---|
| 조회·수정 성공 | 200 OK |
| 생성 성공 | 201 Created (+ 생성된 데이터) |
| 삭제·취소 성공 | 204 No Content |

### 에러 응답

모든 에러는 같은 형식이다. `code`는 아래 [에러 코드](#6-에러-코드) 표를 따른다.

```json
{
  "status": 400,
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다.",
  "fieldErrors": [
    { "field": "title", "message": "제목은 1~100자로 입력해 주세요." }
  ]
}
```

`fieldErrors`는 입력값 검증 실패(400)일 때만 들어간다.

스프링이 먼저 거르는 에러(없는 URL, 허용하지 않는 메서드, 잘못된 Content-Type, 숫자 자리에 문자)도 같은 형식으로 응답한다: `NOT_FOUND`(404), `METHOD_NOT_ALLOWED`(405), `UNSUPPORTED_MEDIA_TYPE`(415), 경로·쿼리 값 형식 오류는 `INVALID_INPUT`(400).

### 장애 시 동작

| 장애 | 영향받는 API | 응답 |
|---|---|---|
| Redis 다운 | 로그인·토큰 재발급·로그아웃만 | `503 SERVICE_UNAVAILABLE` (그 외 API는 Access Token만 검증하므로 정상 동작) |
| S3 다운 | Presigned URL 발급, 레시피 저장(사진 검증) | `503 SERVICE_UNAVAILABLE` |
| DB 다운 | 대부분 | `500 INTERNAL_ERROR` |

`503`은 "잠시 후 다시 시도"를 뜻한다. 프론트는 `503`을 받아도 **로그아웃시키지 않는다**(INTEGRATION.md). 가능하면 `Retry-After` 헤더를 붙인다.

## 1. API 목록

| 기능 | 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| 회원 | POST | `/api/members/signup` | - | 회원가입 |
| 회원 | POST | `/api/auth/login` | - | 로그인, 토큰 발급 |
| 회원 | POST | `/api/auth/refresh` | 쿠키 | Access Token 재발급 |
| 회원 | POST | `/api/auth/logout` | 쿠키 | 로그아웃 |
| 회원 | GET | `/api/members/me` | 필수 | 내 정보 |
| 이미지 | POST | `/api/images/presigned-url` | 필수 | 사진 업로드용 Presigned URL 발급 |
| 레시피 | POST | `/api/recipes` | 필수 | 레시피 등록 |
| 레시피 | GET | `/api/recipes` | 선택 | 목록(최신순), `ingredient`로 재료 검색 |
| 레시피 | GET | `/api/recipes/popular-ingredients` | - | 레시피에 많이 쓰인 재료 TOP N |
| 레시피 | GET | `/api/recipes/{recipeId}` | 선택 | 상세 |
| 레시피 | PUT | `/api/recipes/{recipeId}` | 필수 | 수정 (작성자만) |
| 레시피 | DELETE | `/api/recipes/{recipeId}` | 필수 | 삭제 (작성자만) |
| 좋아요 | POST | `/api/recipes/{recipeId}/likes` | 필수 | 좋아요 |
| 좋아요 | DELETE | `/api/recipes/{recipeId}/likes` | 필수 | 좋아요 취소 |
| 댓글 | GET | `/api/recipes/{recipeId}/comments` | - | 댓글 목록(오래된순) |
| 댓글 | POST | `/api/recipes/{recipeId}/comments` | 필수 | 댓글 작성 |
| 댓글 | DELETE | `/api/comments/{commentId}` | 필수 | 댓글 삭제 (작성자만) |

## 2. 회원

### 회원가입 `POST /api/members/signup`

요청

```json
{
  "email": "cook@example.com",
  "password": "password123",
  "nickname": "두부요리사"
}
```

| 필드 | 규칙 |
|---|---|
| email | 필수, 이메일 형식, 100자 이하 |
| password | 필수, 8~20자. 글자 종류 제한은 없다(영문·숫자·한글·특수문자 모두 허용, 공백 포함 가능). BCrypt 입력 한도(72바이트) 안이다 |
| nickname | 필수, 2~30자. 한글·영문·숫자·공백·`_` 등 글자 제한 없음(출력은 항상 이스케이프). 닉네임 중복은 대소문자 구분 없이 비교(DB 기본 정렬 규칙) |

응답 `201 Created`

```json
{ "memberId": 1, "email": "cook@example.com", "nickname": "두부요리사" }
```

에러: `INVALID_INPUT`(400), `EMAIL_DUPLICATED`(409), `NICKNAME_DUPLICATED`(409)

### 로그인 `POST /api/auth/login`

요청

```json
{ "email": "cook@example.com", "password": "password123" }
```

응답 `200 OK`

```json
{ "accessToken": "eyJhbGciOi...", "tokenType": "Bearer", "expiresIn": 1800 }
```

응답 헤더: `Set-Cookie: refresh_token={Refresh Token}; HttpOnly; SameSite=Strict; Path=/api/auth; Max-Age=1209600`

에러: `INVALID_INPUT`(400), `LOGIN_FAILED`(401), `LOGIN_TOO_MANY_ATTEMPTS`(429)

- 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않고 같은 에러로 응답한다. 이메일이 없을 때도 BCrypt 비교를 한 번 수행해 응답 시간으로도 구분되지 않게 한다.
- 같은 이메일로 5번 실패하면 15분 동안 로그인을 막는다(Redis `login-fail:{email}`). 막힌 동안은 비밀번호가 맞아도 429로 응답한다. 성공하면 실패 횟수를 지운다.

### 토큰 재발급 `POST /api/auth/refresh`

요청 본문 없음. 브라우저가 `refresh_token` 쿠키를 자동으로 보낸다.

서버 처리: 쿠키의 Refresh Token 서명·만료·종류(`type=refresh`) 검증 → Redis `refresh:{memberId}`의 해시와 비교 → 일치하면 새 Access Token·새 Refresh Token 발급, Redis 값 교체(로테이션)

응답 `200 OK`: 로그인 응답과 같은 본문 + 새 `Set-Cookie`

```json
{ "accessToken": "eyJhbGciOi...", "tokenType": "Bearer", "expiresIn": 1800 }
```

에러: `REFRESH_TOKEN_INVALID`(401) — 쿠키가 없음, 서명 오류·만료, Redis에 키가 없음(로그아웃·만료), 저장된 값과 불일치. 불일치(이미 교체된 예전 토큰의 재사용)면 Redis 키도 지워 다시 로그인하게 한다. 실패 시 쿠키를 지우는 `Set-Cookie`(`Max-Age=0`)를 함께 보낸다.

### 로그아웃 `POST /api/auth/logout`

요청 본문 없음. 쿠키의 Refresh Token으로 회원을 찾아 Redis 키를 지우고 쿠키를 삭제한다.

응답 `204 No Content` + `Set-Cookie: refresh_token=; Path=/api/auth; Max-Age=0`

쿠키가 없거나 이미 로그아웃된 상태여도 `204`로 응답한다(여러 번 눌러도 결과가 같음). 이미 발급된 Access Token은 만료(최대 30분)까지 유효하므로, 프론트는 메모리의 Access Token도 함께 지운다.

### 내 정보 `GET /api/members/me`

응답 `200 OK`

```json
{ "memberId": 1, "email": "cook@example.com", "nickname": "두부요리사" }
```

## 3. 이미지

사진은 AWS S3에 저장한다. 서버가 파일을 직접 받지 않고, **업로드용 임시 URL(Presigned URL)**을 발급해 브라우저가 S3에 직접 올린다. 사진은 CloudFront 주소로 공개되므로 화면에서는 응답의 URL로 바로 보여준다.

업로드 순서

1. `POST /api/images/presigned-url`로 업로드 URL과 `objectKey`를 받는다.
2. 받은 `uploadUrl`로 파일을 `PUT` 한다(S3로 직접, 아래 참고).
3. 레시피 등록·수정 요청에 `objectKey`를 `thumbnailKey`, `steps[].imageKey`로 넣는다.
4. 서버는 레시피를 저장할 때 그 객체가 실제로 올라갔는지, 요청한 회원이 발급받은 키인지, 형식·크기가 맞는지 다시 확인한다.
5. 수정 화면에서 **바꾸지 않는 사진**은 상세 응답의 `thumbnailKey`, `steps[].imageKey`를 그대로 다시 보낸다(키를 보내지 않으면 사진이 지워진 것으로 처리).

### Presigned URL 발급 `POST /api/images/presigned-url`

요청

```json
{ "contentType": "image/jpeg", "fileSize": 834512 }
```

| 필드 | 규칙 |
|---|---|
| contentType | 필수. `image/jpeg`, `image/png`, `image/webp` 중 하나 |
| fileSize | 필수, 바이트 단위. 5MB(5,242,880) 이하 (설계 기본값) |

응답 `200 OK`

```json
{
  "uploadUrl": "https://myrecipe-images-example.s3.ap-northeast-2.amazonaws.com/recipes/1/2026/10/02/3f9c1a2e.jpg?X-Amz-Algorithm=...&X-Amz-Signature=...",
  "objectKey": "recipes/1/2026/10/02/3f9c1a2e.jpg",
  "imageUrl": "https://d1234example.cloudfront.net/recipes/1/2026/10/02/3f9c1a2e.jpg",
  "expiresIn": 300
}
```

- `objectKey`: `recipes/{memberId}/{yyyy}/{MM}/{dd}/{UUID}.{확장자}`. 확장자는 `contentType`에서 정한다(`image/jpeg`→`jpg`, `image/png`→`png`, `image/webp`→`webp`). 날짜는 서버(Asia/Seoul) 기준. 회원 ID가 키에 들어가므로 다른 회원이 발급받은 키는 쓸 수 없다.
- `uploadUrl`: 5분 동안만 유효한 업로드 URL (설계 기본값). 발급 시 정한 `contentType`으로만 올릴 수 있다.
- `imageUrl`: 업로드 후 미리보기에 쓰는 공개 URL.

에러: `INVALID_INPUT`(400), `IMAGE_INVALID_TYPE`(400), `IMAGE_TOO_LARGE`(400)

### S3로 업로드 `PUT {uploadUrl}` (우리 API가 아님)

```http
PUT {uploadUrl}
Content-Type: image/jpeg   ← 발급 요청의 contentType과 같아야 함

(파일 바이너리)
```

- 성공하면 S3가 `200 OK`를 준다. `Authorization` 헤더(JWT)는 붙이지 않는다. 서명이 URL에 들어 있다.
- URL이 만료되었거나 `Content-Type`이 다르면 S3가 `403`을 준다. 이때는 Presigned URL을 다시 발급받는다.
- Presigned PUT은 파일 크기를 막지 못한다. 그래서 레시피 저장 때 서버가 실제 크기를 다시 확인한다.

업로드만 하고 레시피에 쓰지 않은 사진은 MVP에서는 정리하지 않는다(확장 과제).

## 4. 레시피

### 레시피 등록 `POST /api/recipes`

요청

```json
{
  "title": "두부조림",
  "description": "밥반찬으로 좋은 간단한 두부조림",
  "thumbnailKey": "recipes/1/2026/10/02/3f9c1a2e.jpg",
  "ingredients": [
    { "name": "두부", "amount": "1모" },
    { "name": "간장", "amount": "3큰술" },
    { "name": "대파", "amount": "약간" }
  ],
  "steps": [
    { "content": "두부를 1cm 두께로 썬다.", "imageKey": null },
    { "content": "팬에 두부를 노릇하게 굽는다.", "imageKey": "recipes/1/2026/10/02/7ab1c0d4.jpg" },
    { "content": "양념장을 붓고 졸인다.", "imageKey": null }
  ]
}
```

| 필드 | 규칙 |
|---|---|
| title | 필수, 1~100자 |
| description | 선택, 2,000자 이하 |
| thumbnailKey | 선택, Presigned URL 발급에서 받은 `objectKey`. 업로드가 끝난 상태여야 함 |
| ingredients | 필수, 1~30개. 같은 재료 이름 중복 불가 |
| ingredients[].name | 필수, 1~50자. 앞뒤 공백을 지운 이름으로 재료를 찾고, 없으면 새로 만든다 |
| ingredients[].amount | 선택, 30자 이하 ("1모", "약간") |
| steps | 필수, 1~30개. 배열 순서가 조리 순서 |
| steps[].content | 필수, 1~1,000자 |
| steps[].imageKey | 선택, `thumbnailKey`와 같은 규칙 |

응답 `201 Created`: [레시피 상세](#레시피-상세-get-apirecipesrecipeid)와 같은 형식

에러: `INVALID_INPUT`(400), `INGREDIENT_DUPLICATED`(400), `IMAGE_NOT_FOUND`(400), `IMAGE_INVALID_TYPE`(400), `IMAGE_TOO_LARGE`(400), `IMAGE_ALREADY_USED`(400)

사진 키는 서버가 저장 전에 확인한다: 키가 `recipes/{내 memberId}/`로 시작하는지, S3에 객체가 있는지, 실제 형식·크기가 규칙에 맞는지, 파일 앞부분의 바이트가 실제 이미지(JPEG·PNG·WebP)인지.

- **한 사진 키는 한 곳에만 쓴다.** 같은 요청 안에서 키가 겹치거나(대표 사진과 단계 사진 포함), 다른 레시피가 이미 쓰는 키면 `IMAGE_ALREADY_USED`. (같은 키를 두 레시피가 쓰면 한쪽을 지울 때 다른 쪽 사진이 사라지기 때문이다.)
- 수정 요청에서 **이 레시피가 이미 쓰고 있는 키**는 다시 확인하지 않는다(S3 호출 절약). 새 키만 확인한다.

### 레시피 목록·재료 검색 `GET /api/recipes`

| 쿼리 파라미터 | 설명 |
|---|---|
| ingredient | 선택. 재료 이름과 정확히 일치하는 재료가 들어간 레시피만 조회(앞뒤 공백 제거, 대소문자 구분 없음). 한 번에 재료 하나만 검색한다. 빈 값이면 검색 조건이 없는 것으로 보고 전체 목록. 예: `?ingredient=두부` |
| page, size | 페이지 (기본 0, 20) |

정렬은 최신 등록순이다. 일치하는 재료가 없으면 빈 목록을 반환한다(에러 아님).

응답 `200 OK` (페이지 형식, `content` 항목)

```json
{
  "recipeId": 10,
  "title": "두부조림",
  "thumbnailUrl": "https://d1234example.cloudfront.net/recipes/1/2026/10/02/3f9c1a2e.jpg",
  "writer": { "memberId": 1, "nickname": "두부요리사" },
  "likeCount": 12,
  "createdAt": "2026-10-02T11:07:00"
}
```

### 인기 재료 `GET /api/recipes/popular-ingredients`

> 2026-11-08 추가. 레시피에 많이 쓰인 재료를 레시피 수 순으로 보여 준다. 화면에서는 검색창 아래 재료 버튼으로 쓰고, 누르면 `GET /api/recipes?ingredient={name}`으로 검색한다.

| 쿼리 파라미터 | 설명 |
|---|---|
| size | 선택. 기본 10, 최대 20. 20을 넘으면 20으로 줄여서 처리하고(에러 아님), 1 미만이거나 숫자가 아니면 `400 INVALID_INPUT` |

- 정렬은 레시피 수가 많은 순, 같으면 재료 이름순이다. 어떤 레시피에도 쓰이지 않은 재료는 나오지 않는다. 레시피가 없으면 빈 배열이다.
- **페이지 형식이 아니라 배열로 응답한다.** 정해진 개수만 보여 주는 상위 목록이라 공통 규칙(목록은 페이지 형식)의 예외로 둔다.
- 경로가 `/api/recipes/{recipeId}`와 겹쳐 보이지만, 스프링은 고정 경로를 경로 변수보다 먼저 매칭한다.

응답 `200 OK`

```json
[
  { "name": "두부", "recipeCount": 12 },
  { "name": "대파", "recipeCount": 9 }
]
```

### 레시피 상세 `GET /api/recipes/{recipeId}`

응답 `200 OK`

```json
{
  "recipeId": 10,
  "title": "두부조림",
  "description": "밥반찬으로 좋은 간단한 두부조림",
  "thumbnailUrl": "https://d1234example.cloudfront.net/recipes/1/2026/10/02/3f9c1a2e.jpg",
  "thumbnailKey": "recipes/1/2026/10/02/3f9c1a2e.jpg",
  "writer": { "memberId": 1, "nickname": "두부요리사" },
  "ingredients": [
    { "name": "두부", "amount": "1모" },
    { "name": "간장", "amount": "3큰술" },
    { "name": "대파", "amount": "약간" }
  ],
  "steps": [
    { "order": 1, "content": "두부를 1cm 두께로 썬다.", "imageUrl": null, "imageKey": null },
    { "order": 2, "content": "팬에 두부를 노릇하게 굽는다.", "imageUrl": "https://d1234example.cloudfront.net/recipes/1/2026/10/02/7ab1c0d4.jpg", "imageKey": "recipes/1/2026/10/02/7ab1c0d4.jpg" },
    { "order": 3, "content": "양념장을 붓고 졸인다.", "imageUrl": null, "imageKey": null }
  ],
  "likeCount": 12,
  "likedByMe": false,
  "commentCount": 3,
  "createdAt": "2026-10-02T11:07:00",
  "updatedAt": "2026-10-02T11:07:00"
}
```

`likedByMe`는 토큰 없이 조회하면 항상 `false`다. `thumbnailKey`, `imageKey`는 수정 화면이 바꾸지 않은 사진을 그대로 다시 보내기 위한 값이다(사진 주소 자체가 공개이므로 키를 숨길 필요는 없다).

**조리 순서 `order`**: 요청 배열의 순서대로 1부터 서버가 매긴다(요청에 `order`를 보내지 않는다).

에러: `RECIPE_NOT_FOUND`(404)

### 레시피 수정 `PUT /api/recipes/{recipeId}`

요청 형식과 규칙은 등록과 같다. 재료 목록과 조리 순서는 **보낸 목록으로 통째로 교체**한다.

응답 `200 OK`: 레시피 상세 형식

에러: `INVALID_INPUT`(400), `INGREDIENT_DUPLICATED`(400), `IMAGE_NOT_FOUND`(400), `IMAGE_INVALID_TYPE`(400), `IMAGE_TOO_LARGE`(400), `IMAGE_ALREADY_USED`(400), `RECIPE_FORBIDDEN`(403), `RECIPE_NOT_FOUND`(404)

수정 전 키 목록과 새 키 목록을 비교해, **새 목록에 없는 키**(교체·삭제된 사진)를 지울 대상으로 삼는다. 교체되어 더 이상 쓰지 않는 사진은 DB 저장이 끝난 뒤 S3에서 지운다. 지우기에 실패해도 수정은 성공으로 처리하고 로그만 남긴다.

### 레시피 삭제 `DELETE /api/recipes/{recipeId}`

레시피의 댓글, 좋아요, 재료 연결, 조리 순서를 함께 지운다. 재료(`ingredient`) 자체는 다른 레시피가 쓸 수 있으므로 지우지 않는다. 레시피의 사진은 DB 삭제가 끝난 뒤 S3에서 지우고, 실패하면 로그만 남긴다.

응답 `204 No Content`

에러: `RECIPE_FORBIDDEN`(403), `RECIPE_NOT_FOUND`(404)

## 5. 좋아요·댓글

### 좋아요 `POST /api/recipes/{recipeId}/likes`

자기 레시피에도 누를 수 있다(제한 없음).

응답 `201 Created`

```json
{ "recipeId": 10, "likeCount": 13, "likedByMe": true }
```

에러: `RECIPE_NOT_FOUND`(404), `LIKE_ALREADY_EXISTS`(409)

### 좋아요 취소 `DELETE /api/recipes/{recipeId}/likes`

응답 `204 No Content`

에러: `RECIPE_NOT_FOUND`(404), `LIKE_NOT_FOUND`(404)

### 댓글 목록 `GET /api/recipes/{recipeId}/comments`

오래된 순, 페이지 형식. `content` 항목:

```json
{
  "commentId": 5,
  "content": "주말에 해봤는데 맛있어요!",
  "writer": { "memberId": 2, "nickname": "자취생" },
  "createdAt": "2026-10-02T12:00:00"
}
```

에러: `RECIPE_NOT_FOUND`(404)

### 댓글 작성 `POST /api/recipes/{recipeId}/comments`

요청

```json
{ "content": "주말에 해봤는데 맛있어요!" }
```

| 필드 | 규칙 |
|---|---|
| content | 필수, 1~500자 |

응답 `201 Created`: 댓글 목록 항목과 같은 형식

에러: `INVALID_INPUT`(400), `RECIPE_NOT_FOUND`(404)

### 댓글 삭제 `DELETE /api/comments/{commentId}`

응답 `204 No Content`

에러: `COMMENT_FORBIDDEN`(403), `COMMENT_NOT_FOUND`(404)

## 6. 에러 코드

`ErrorCode` enum과 1:1로 맞춘다.

| 코드 | 상태 | 메시지 | 발생 |
|---|---|---|---|
| INVALID_INPUT | 400 | 입력값이 올바르지 않습니다. | 요청 검증 실패 (`fieldErrors` 포함) |
| UNAUTHORIZED | 401 | 로그인이 필요합니다. | 인증 필수 API에 토큰 없음 |
| TOKEN_INVALID | 401 | 유효하지 않은 토큰입니다. | 서명 오류, 형식 오류, Access Token이 아닌 토큰 |
| TOKEN_EXPIRED | 401 | 로그인이 만료되었습니다. | 토큰 유효 시간 지남 |
| LOGIN_FAILED | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. | 로그인 실패 |
| LOGIN_TOO_MANY_ATTEMPTS | 429 | 로그인 시도가 너무 많습니다. 15분 후 다시 시도해 주세요. | 같은 이메일로 5번 연속 실패 |
| REFRESH_TOKEN_INVALID | 401 | 다시 로그인해 주세요. | 토큰 재발급 실패 |
| EMAIL_DUPLICATED | 409 | 이미 사용 중인 이메일입니다. | 회원가입 |
| NICKNAME_DUPLICATED | 409 | 이미 사용 중인 닉네임입니다. | 회원가입 |
| IMAGE_INVALID_TYPE | 400 | 지원하지 않는 이미지 형식입니다. | Presigned URL 발급, 레시피 저장 (Content-Type 또는 실제 파일 내용이 이미지가 아님) |
| IMAGE_TOO_LARGE | 400 | 이미지는 5MB 이하만 올릴 수 있습니다. | Presigned URL 발급, 레시피 저장 |
| IMAGE_ALREADY_USED | 400 | 이미 다른 곳에서 사용 중인 사진입니다. 사진을 다시 올려 주세요. | 레시피 저장 (키가 요청 안에서 겹치거나 다른 레시피가 이미 사용) |
| IMAGE_NOT_FOUND | 400 | 업로드된 사진을 찾을 수 없습니다. 다시 올려 주세요. | 레시피 저장 (업로드 안 됨, 다른 회원의 키) |
| INGREDIENT_DUPLICATED | 400 | 같은 재료가 두 번 입력되었습니다. | 레시피 등록·수정 |
| RECIPE_NOT_FOUND | 404 | 레시피를 찾을 수 없습니다. | 레시피·좋아요·댓글 API |
| RECIPE_FORBIDDEN | 403 | 레시피 작성자만 수정·삭제할 수 있습니다. | 레시피 수정·삭제 |
| LIKE_ALREADY_EXISTS | 409 | 이미 좋아요한 레시피입니다. | 좋아요 |
| LIKE_NOT_FOUND | 404 | 좋아요한 기록이 없습니다. | 좋아요 취소 |
| COMMENT_NOT_FOUND | 404 | 댓글을 찾을 수 없습니다. | 댓글 삭제 |
| COMMENT_FORBIDDEN | 403 | 댓글 작성자만 삭제할 수 있습니다. | 댓글 삭제 |
| FORBIDDEN | 403 | 접근 권한이 없습니다. | 인증은 됐지만 권한 없음(스프링 시큐리티 단계). 지금은 쓰는 곳 없음 — 도메인 권한은 `*_FORBIDDEN`을 쓴다 |
| NOT_FOUND | 404 | 요청한 주소를 찾을 수 없습니다. | 존재하지 않는 URL |
| METHOD_NOT_ALLOWED | 405 | 지원하지 않는 요청 방식입니다. | URL은 있으나 HTTP 메서드가 다름 |
| UNSUPPORTED_MEDIA_TYPE | 415 | 지원하지 않는 요청 형식입니다. | `application/json`이 아닌 본문 |
| SERVICE_UNAVAILABLE | 503 | 잠시 후 다시 시도해 주세요. | Redis·S3 같은 외부 의존 장애 |
| INTERNAL_ERROR | 500 | 일시적인 오류가 발생했습니다. | 예상하지 못한 서버 오류 (원인은 로그에만 남김) |

## 7. 권한 정책 (소유권 검증)

| 동작 | 누가 | 위반 시 |
|---|---|---|
| 레시피 수정·삭제 | 작성자 본인 | `RECIPE_FORBIDDEN` 403 |
| 댓글 삭제 | 댓글 작성자 본인 | `COMMENT_FORBIDDEN` 403 |
| 사진 키(`imageKey`) 사용 | 그 키를 발급받은 본인(키에 memberId 포함) | `IMAGE_NOT_FOUND` 400 |

- 존재 여부 확인(404)이 권한 확인(403)보다 먼저다. 없는 리소스는 항상 404.
- 서비스 계층은 스프링의 `AccessDeniedException`이 아니라 `BusinessException(*_FORBIDDEN)`을 던진다. (시큐리티 단계의 `AccessDeniedException`은 `FORBIDDEN`으로 응답)
- 인증 오류의 응답 형식은 필터 단계에서 나는 것도 `GlobalExceptionHandler`와 같은 `ErrorResponse`다.
