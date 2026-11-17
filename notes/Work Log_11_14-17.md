# 작업일지 — 2026-11-14 ~ 11-17

> 이전 기록은 `Work Log_11_06-09.md`, 단계별 할 일은 `docs/ROADMAP.md`, 프로젝트 방향은 `docs/DIRECTION.md`, 설명 확인 기록은 `EXPLAIN_CHECK.md` 참고.

## 지금의 위치

- 1단계(공통 기반 + 회원가입/로그인) 진행 중
- 이 기간에 끝낸 것: 409 처리 PR 병합, **JWT Access Token 발급·검증** (`JwtProvider`, `JwtConfig`)
- 테스트: **28개 모두 통과** (로컬 `./gradlew test`, Docker Desktop만 켜져 있으면 됨)
- 설명 확인 미해결 질문: **0개**
- 브랜치: `feature/jwt-provider` (커밋 `45c4c6f`, **푸시 전**)
- 다음: 로그인 API `POST /api/auth/login` → JWT 필터 + `GET /api/members/me`

---

## 1. 11-14: 409 PR 병합, JWT 작업 시작

- `feature/signup-duplicate-409`를 PR #6, #7로 병합했다. 로컬 `main`을 받은 뒤 새 브랜치 `feature/jwt-provider`를 만들었다.
- 로그인·JWT(답안지 장면 9)는 설명 확인할 양을 줄이려고 세 단위로 나눴다.
  1. JWT 발급·검증 클래스 ← 이번에 끝냄
  2. 로그인 API
  3. JWT 필터 + `/api/members/me` (403 대신 401로 응답하는 한계도 여기서 고친다)

## 2. 만든 것: JWT 발급·검증

| 파일 | 역할 |
|---|---|
| `global/security/JwtConfig` | `jwt.*` 설정. 시크릿이 없거나 32바이트 미만이면 **앱 시작 실패** |
| `global/config/JwtPropertiesConfig` | `JwtConfig`를 빈으로 등록 |
| `global/security/JwtProvider` | Access Token 발급·검증. 성공하면 회원 id, 만료면 `TOKEN_EXPIRED`, 그 밖의 문제는 `TOKEN_INVALID` |
| `support/IntegrationTestSupport` | 통합 테스트에 테스트용 JWT 시크릿 주입 (CI에는 `.env`가 없음) |

- 토큰 내용: `sub`=회원 id, `type`=`access`, `iss`=`myrecipe`, `iat`, `exp`
- 검증 규칙(INTEGRATION "JWT 필터 동작"):
  - HS256만 받는다. 헤더의 `alg`는 믿지 않는다.
  - 발급자(`iss`)를 확인한다.
  - `type`이 `access`인 토큰만 받는다(QA9).
- Refresh Token 발급은 아직 쓰지 않아서 만들지 않았다. Refresh Token 단계에서 추가한다.
- **결정**: 문서에 값이 없던 발급자(`iss`)를 앱 이름과 같은 `myrecipe`로 정했다. INTEGRATION에 기록했다.

### 테스트 (새로 9개)
| 테스트 | 확인한 것 |
|---|---|
| `JwtProviderTest` (6) | 정상 발급·검증, 만료(시계를 1시간 전으로 돌려 발급), 다른 키로 위조, Refresh Token을 Access 자리에 사용, HS512 서명, 깨진 문자열 |
| `JwtConfigTest` (3) | 짧은 키, 키 없음, 정상 키 |

## 3. 11-17: 테스트 실행과 설명 확인

- Docker Desktop만 켜고 `./gradlew test` → 28개 통과. `docker compose`(개발용 MySQL·Redis)는 테스트에 필요 없고, 개발 서버(`bootRun`)를 띄울 때만 쓴다.

### 설명 확인
| # | 질문 | 처음 답 | 결과 |
|---|---|---|---|
| ① | `JwtProvider`는 무엇을 하나 | Access Token을 만들고 검증한다 | 통과 |
| ② | `type`을 넣고 확인하는 이유 | "재로그인이 필요한지 판별" (만료 처리와 헷갈림) | 힌트 후 통과: 같은 키로 서명해서 서명만으로는 구별이 안 된다. 확인하지 않으면 14일짜리 Refresh Token을 Access Token처럼 쓸 수 있다 |
| ③ | `JWT_SECRET`이 없으면? 조용히 실행되는 것보다 왜 나은가 | 질문을 이해하지 못함 | 질문을 쉽게 바꾼 뒤 통과: 켜지다가 멈춘다. 짧은 키는 위조가 쉬운데 조용히 돌면 아무도 모른다 |

- 다시 볼 것: 재로그인 판단은 `type`이 아니라 **만료 여부**(`TOKEN_EXPIRED` → 재발급, `TOKEN_INVALID` → 로그아웃)로 한다.

## 4. 기록할 점

- **새 세션에서는 폴더 삭제 권한이 다시 필요하다.**
  - 권한 없이 커밋하면 git이 `.git/HEAD.lock` 등 임시 파일을 지우지 못한다. 그러면 IntelliJ의 git도 "File exists"로 막힌다.
  - 이번에는 권한을 받아 잠금 파일 2개와 임시 파일 26개를 지우고 `git fsck`로 확인했다.
  - 앞으로는 커밋 전에 권한부터 요청한다.
- **질문 ③의 문장이 어려웠다.** 설명 확인 질문은 용어(`JWT_SECRET`)가 무엇인지 먼저 짚고 묻는다.

## 5. 새 채팅에서 이어갈 때

1. 데스크톱 앱의 **"Add folder"**로 `myrecipe-backend`를 연결한다.
2. "AGENTS.md랑 ROADMAP 읽고 다음 할 일부터 이어서 하자"로 시작한다.
3. 커밋할 때 폴더 삭제 권한을 승인한다.

## 다음 할 일

- [ ] `feature/jwt-provider` 푸시·PR, CI 통과 확인
- [ ] 로그인 API `POST /api/auth/login`: 이메일·비밀번호 확인 → Access Token 발급 (+ 테스트)
- [ ] JWT 필터 + `GET /api/members/me` + 필터 단계 401 응답 (컨트롤러 테스트 4번 기대값 403 → 401)
- [ ] 이후: JWT `jti`, Refresh Token + Redis(단순 방식) + 통합 테스트 Redis 컨테이너
