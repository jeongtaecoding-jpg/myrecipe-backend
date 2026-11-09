# 레시피 사이트 ERD (MVP)

> 기준일: 2026-10-02 · 상태: 설계 (구현 전) · 최종 수정: 2026-11-08 (조회 방식 표시, 인기 재료 쿼리)
> 2026-11-08 판정: 로그인 실패 잠금(429 `LOGIN_TOO_MANY_ATTEMPTS`, `login-fail`), Redis 장애 시 503, Refresh 재발급 Lua 원자화, `ErrorCodeSpecTest`는 **MVP 이후로 미뤘다**(ROADMAP "MVP 이후 (미룬 항목)"). 이 문서의 해당 설계는 나중에 구현할 목표로 남겨 두며, MVP에서는 구현하지 않는다.

MVP 기능(회원가입/로그인, 레시피 CRUD, 좋아요·댓글, 재료 검색)에 필요한 MySQL 테이블 7개와, Redis에 저장하는 Refresh Token·로그인 실패 횟수, AWS S3에 저장하는 사진이다.

## 설계 원칙

- **재료는 별도 테이블**: 재료로 검색하려면 레시피 본문 텍스트가 아니라 `ingredient` 행으로 저장해야 한다. 등록 시 같은 이름(앞뒤 공백 제거 후)이 있으면 재사용하고 없으면 만든다.
- **재료 양은 문자열**: "1모", "2큰술", "약간"처럼 입력한 그대로 저장한다. 단위 계산은 하지 않는다.
- **사진은 키만 저장**: DB에는 S3 객체 키(`recipes/1/2026/10/02/3f9c1a2e.jpg`)만 저장하고, 응답할 때 공개 주소를 앞에 붙여 URL을 만든다. 저장소나 사진 주소가 바뀌어도(예: CloudFront 도메인 변경) DB를 고칠 필요가 없다.
- **좋아요 수는 레시피에 캐시**: 목록마다 `COUNT(*)`를 돌리지 않도록 `recipe.like_count`를 두고, DB에서 원자적으로 증감한다.
- **공통 컬럼**: 모든 테이블은 `id BIGINT` PK(auto increment), `created_at`, `updated_at`을 가진다. JPA에서는 `BaseTimeEntity`로 묶어 상속한다. 아래 표에서는 생략한다.
- **FK**: 관계 컬럼에는 DB FK 제약을 둔다. 삭제는 `ON DELETE CASCADE`에 맡기지 않고 서비스에서 자식부터 지운다.

## ERD 다이어그램

```mermaid
erDiagram
    member ||--o{ recipe : "작성"
    recipe ||--|{ recipe_step : "조리 순서"
    recipe ||--|{ recipe_ingredient : ""
    ingredient ||--o{ recipe_ingredient : ""
    member ||--o{ recipe_like : ""
    recipe ||--o{ recipe_like : ""
    member ||--o{ comment : ""
    recipe ||--o{ comment : ""

    member {
        bigint id PK
        varchar email UK
        varchar password
        varchar nickname UK
    }
    recipe {
        bigint id PK
        bigint member_id FK
        varchar title
        text description
        varchar thumbnail_key
        int like_count
    }
    recipe_step {
        bigint id PK
        bigint recipe_id FK
        int step_order
        text content
        varchar image_key
    }
    ingredient {
        bigint id PK
        varchar name UK
    }
    recipe_ingredient {
        bigint id PK
        bigint recipe_id FK
        bigint ingredient_id FK
        varchar amount
        int sort_order
    }
    recipe_like {
        bigint id PK
        bigint member_id FK
        bigint recipe_id FK
    }
    comment {
        bigint id PK
        bigint recipe_id FK
        bigint member_id FK
        varchar content
    }
```

## 테이블 명세

### 회원

| 테이블 | 컬럼 | 타입 | 제약·설명 |
| --- | --- | --- | --- |
| member | email | VARCHAR(100) | NOT NULL, UNIQUE |
| member | password | VARCHAR(255) | NOT NULL, BCrypt 해시 |
| member | nickname | VARCHAR(30) | NOT NULL, UNIQUE |

### 레시피·조리 순서

| 테이블 | 컬럼 | 타입 | 제약·설명 |
| --- | --- | --- | --- |
| recipe | member\_id | BIGINT FK → member | NOT NULL, 작성자 |
| recipe | title | VARCHAR(100) | NOT NULL |
| recipe | description | TEXT | 소개글, NULL 허용 |
| recipe | thumbnail\_key | VARCHAR(300) | 대표 사진의 S3 객체 키, NULL 허용. UNIQUE (`uk_recipe_thumbnail_key`, NULL은 여러 개 가능) |
| recipe | like\_count | INT | NOT NULL, 기본 0 |
| recipe\_step | recipe\_id | BIGINT FK → recipe | NOT NULL |
| recipe\_step | step\_order | INT | NOT NULL, 1부터. (recipe\_id, step\_order) UNIQUE |
| recipe\_step | content | TEXT | NOT NULL, 조리 설명 |
| recipe\_step | image\_key | VARCHAR(300) | 단계 사진의 S3 객체 키, NULL 허용. UNIQUE (`uk_recipe_step_image_key`) |

### 재료

| 테이블 | 컬럼 | 타입 | 제약·설명 |
| --- | --- | --- | --- |
| ingredient | name | VARCHAR(50) | NOT NULL, UNIQUE (두부, 대파…) |
| recipe\_ingredient | recipe\_id | BIGINT FK → recipe | NOT NULL. (recipe\_id, ingredient\_id) UNIQUE |
| recipe\_ingredient | ingredient\_id | BIGINT FK → ingredient | NOT NULL. (ingredient\_id, recipe\_id) 복합 INDEX (재료 검색용: 재료로 레시피 id를 찾을 때 테이블 접근 없이 인덱스만으로 처리) |
| recipe\_ingredient | amount | VARCHAR(30) | "1모", "2큰술", "약간". NULL 허용 |
| recipe\_ingredient | sort\_order | INT | NOT NULL, 화면 표시 순서 |

### 좋아요·댓글

| 테이블 | 컬럼 | 타입 | 제약·설명 |
| --- | --- | --- | --- |
| recipe\_like | member\_id | BIGINT FK → member | NOT NULL. (member\_id, recipe\_id) UNIQUE → 중복 좋아요 방지 |
| recipe\_like | recipe\_id | BIGINT FK → recipe | NOT NULL |
| comment | recipe\_id | BIGINT FK → recipe | NOT NULL. (recipe\_id, created\_at) 복합 INDEX (레시피별 댓글 목록 + 정렬) |
| comment | member\_id | BIGINT FK → member | NOT NULL |
| comment | content | VARCHAR(500) | NOT NULL |

## 기능별 핵심 쿼리

| 기능 | 테이블 | 핵심 |
| --- | --- | --- |
| 재료로 검색 | ingredient, recipe\_ingredient, recipe | 재료 이름으로 `ingredient.id`를 찾고, 그 재료가 연결된 레시피를 최신순 페이징 (MyBatis) |
| 좋아요 | recipe\_like, recipe | 행 추가와 `like_count + 1`을 한 트랜잭션에서. 중복은 UNIQUE 위반으로 거부 |
| 레시피 목록 | recipe, member | 작성자 닉네임을 fetch join, 페이징. 재료·단계는 상세에서만 조회 |
| 레시피 상세 | recipe, recipe\_step, recipe\_ingredient, ingredient | 단계와 재료를 각각 조회해 N+1 방지 |
| 인기 재료 | recipe\_ingredient, ingredient | 재료별 레시피 수를 GROUP BY로 세어 상위 N개 (MyBatis, 2026-11-08 추가) |

재료 검색 예시:

```sql
SELECT r.id, r.title, r.thumbnail_key, r.like_count, r.created_at
FROM recipe r
JOIN recipe_ingredient ri ON ri.recipe_id = r.id
JOIN ingredient i ON i.id = ri.ingredient_id
WHERE i.name = '두부'
ORDER BY r.created_at DESC, r.id DESC
LIMIT 20 OFFSET 0;
```

좋아요 수 증가 예시:

```sql
UPDATE recipe SET like_count = like_count + 1 WHERE id = ?;
```

인기 재료 예시 (2026-11-08 추가). `(recipe_id, ingredient_id)` UNIQUE 때문에 한 레시피에 같은 재료가 두 번 연결되지 않으므로 `COUNT(*)`가 곧 레시피 수다. `(ingredient_id, recipe_id)` 인덱스로 묶을 수 있는지 실행 계획(`EXPLAIN`)으로 확인한다.

```sql
SELECT i.name, COUNT(*) AS recipe_count
FROM recipe_ingredient ri
JOIN ingredient i ON i.id = ri.ingredient_id
GROUP BY i.id, i.name
ORDER BY recipe_count DESC, i.name ASC
LIMIT 10;
```

## Redis 저장 데이터 (테이블 아님)

Refresh Token과 로그인 실패 횟수는 MySQL이 아니라 Redis에 저장한다. 이유는 아키텍처 문서의 "Redis를 쓰는 이유"를 따른다.

| 키 | 값 | TTL | 설명 |
| --- | --- | --- | --- |
| `refresh:{memberId}` | Refresh Token의 SHA-256 해시 | 14일 (Refresh Token 유효 기간과 같음) | 로그인·재발급 때 새 값으로 덮어씀(로테이션), 로그아웃 때 삭제. 회원별 1개 |
| `login-fail:{email}` | 로그인 실패 횟수 (숫자) | 15분 (첫 실패 때 설정) | 실패할 때 `INCR`, 5 이상이면 로그인 거부, 성공하면 삭제 |

- 토큰 원문은 저장하지 않는다. 재발급 요청이 오면 받은 토큰을 해시해 저장된 값과 비교한다.
- 키가 없으면(로그아웃·만료) 재발급을 거부한다.

## AWS S3 저장 데이터 (테이블 아님)

| 항목 | 값 |
| --- | --- |
| 버킷 | `STORAGE_BUCKET` 값 (예: `myrecipe-images-본인아이디`, 서울 리전, 비공개) |
| 객체 키 | `recipes/{memberId}/{yyyy}/{MM}/{dd}/{UUID}.{확장자}` |
| 접근 권한 | 버킷은 비공개(퍼블릭 액세스 차단). 읽기는 CloudFront만(OAC), 업로드는 Presigned URL로만, 앱 IAM 사용자는 `recipes/*`만 |
| 공개 URL | `{STORAGE_PUBLIC_URL}/{객체 키}` (CloudFront 주소) |
| DB와의 연결 | `recipe.thumbnail_key`, `recipe_step.image_key`에 객체 키 저장 |

- 키에 회원 ID를 넣어, 레시피 저장 시 "내가 발급받은 키인지"를 확인한다.
- 원래 파일 이름은 쓰지 않는다(중복·이상한 문자 방지).
- 레시피 삭제·사진 교체 시 DB 작업이 끝난 뒤 객체를 지운다. 레시피에 쓰이지 않은 객체(업로드만 하고 저장 안 함, 삭제 실패)는 MVP에서 정리하지 않는다.

## 인덱스·제약 이름 규칙 (2026-11-06 추가)

- 목록은 `ORDER BY created_at DESC, id DESC`로 정렬하므로 `recipe(created_at)`에 INDEX를 둔다.
- UNIQUE·INDEX에는 이름을 붙인다: `uk_member_email`, `uk_member_nickname`, `uk_ingredient_name`, `uk_recipe_ingredient`, `uk_recipe_like`, `uk_recipe_thumbnail_key`, `uk_recipe_step_image_key`, `idx_recipe_created_at`, `idx_comment_recipe_created`. 동시 요청으로 UNIQUE 위반이 났을 때 **어느 제약인지 이름으로 구분**해 `EMAIL_DUPLICATED`/`NICKNAME_DUPLICATED`/`LIKE_ALREADY_EXISTS`로 바꾸기 위해서다. (MySQL이 알려 주는 제약 이름 형식은 통합 테스트로 확인한다 → 2026-11-09 확인: MySQL 8.4는 `for key 'member.uk_member_email'`처럼 **테이블 이름을 앞에 붙인다**. 그래서 이름 비교는 `uk_...` 그대로와 `테이블.uk_...` 둘 다 받는다)
- 스키마는 2단계 시작 전에 Flyway로 관리한다(`V1__init.sql`을 이 ERD로 작성, `ddl-auto: validate`). 그 전까지의 `update`는 학습용 임시 설정이다.

## 문자셋·정렬 규칙과 저장 규칙 (2026-11-06 추가)

- **문자셋/정렬**: DB·테이블 모두 `utf8mb4` / `utf8mb4_0900_ai_ci`(MySQL 8.4 기본값). 이 정렬은 **대소문자·악센트를 구분하지 않으므로** `UNIQUE`(email, nickname, ingredient.name)와 `WHERE name = ?` 비교도 구분하지 않는다("Tofu"="tofu"). 의도한 동작이다. 한글 비교는 정확히 일치하는 글자만 같다("달걀"≠"계란").
- **사진 키 유일성**: 한 키는 `recipe.thumbnail_key`와 `recipe_step.image_key`를 통틀어 한 곳에만 쓴다(레시피를 지울 때 다른 레시피의 사진이 지워지는 사고 방지). 두 컬럼의 UNIQUE는 각 테이블 안에서만 막으므로, 테이블 사이의 중복(대표 사진 ↔ 단계 사진)은 서비스가 확인해 `IMAGE_ALREADY_USED`로 거부한다.
- **재료 get-or-create 동시성**: 같은 새 재료를 두 요청이 동시에 만들면 `uk_ingredient_name` 위반이 난다. 위반을 잡아서 다시 조회하는 방식은 트랜잭션이 망가지므로 쓰지 않고, `INSERT IGNORE INTO ingredient (name, created_at, updated_at) VALUES (?, ?, ?)`(네이티브 쿼리) 후 `findByName`으로 읽는다. 이미 있으면 무시되고, 없으면 만들어진다.
- 레시피 삭제 시 자식 지우는 순서: 좋아요·댓글(이벤트 리스너) → `recipe_ingredient` → `recipe_step` → `recipe`. 재료(`ingredient`)는 지우지 않는다.
- 댓글 목록 정렬은 `created_at ASC, id ASC`(오래된 순).

