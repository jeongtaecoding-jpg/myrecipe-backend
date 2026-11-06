# LMS v1·v2 참고 반영 내역 (2026-11-06)

docs의 비어 있던 결정 사항을 LMS(v1 단일 앱, v2 MSA) 방식으로 채우고, 더 나은 방식이 있으면 기존 결정도 바꿨다. **코드는 아직 바꾸지 않았다**(ROADMAP 1단계의 "문서 변경에 따른 코드 후속 작업" 참고). 마음에 안 드는 항목은 되돌릴 수 있다.

## 채택
| 항목 | 출처 | 반영 문서 |
|---|---|---|
| Testcontainers MySQL 통합 테스트 기반 클래스, 테스트 방식 결정(단위 + B + C) | v2 `MySqlIntegrationTest` | CODE_CONVENTION 테스트 |
| 필터·시큐리티 오류를 `HandlerExceptionResolver`로 위임해 `ErrorResponse` 통일, 401/403 핸들러 | v1 `SecurityExceptionHandler` | CODE_CONVENTION 예외, API_SPEC |
| Redis Lua 원자적 refresh 교체 | v2 Redis refresh repo | architecture Redis |
| Redis 장애 → 503(해당 API만), 프론트는 503에서 로그아웃 안 함 | v2 | API_SPEC 장애, INTEGRATION |
| `@ConfigurationProperties` record `JwtConfig`, 시크릿 길이 검증 | v2 | ROADMAP |
| 알 수 없는 JSON 필드 거부 | v2 | API_SPEC 공통 |
| UNIQUE 위반 → 구체 코드(제약 이름) | v1 `DataIntegrityViolation` 처리를 개선 | CODE_CONVENTION, ERD |
| 로그 레벨·`X-Request-Id`/MDC, 프로필 local/prod, 타임아웃, batch fetch size, 가상 스레드, Redis AOF/noeviction | v2 | CODE_CONVENTION, INTEGRATION, architecture |
| 인덱스·정렬 안정화(`created_at DESC, id DESC`), 페이지 파라미터 규칙 | v2 규칙 | ERD, API_SPEC |
| 코드·프론트 컨벤션(B·F 규칙 중 해당분), 권한 정책 표 | v2 컨벤션 | CODE_CONVENTION, INTEGRATION, API_SPEC 7장 |
| Flyway 도입 계획 | v2 | ERD, ROADMAP |

## 채택하지 않음 (이유)
- RSA/kid/aud: 앱이 하나라 HS256으로 충분. 응답 래퍼·`E01` 코드: 지금의 `ErrorResponse`+원본 데이터 방식 유지. 1-based 페이지: 0-based 유지. 게이트웨이 헤더 필터, outbox, QueryDSL, 역할별 로그인, role 클레임, Idempotency-Key, soft delete: MVP 범위 밖.
- v1의 약점은 복사하지 않음: 토큰 type/jti 없음, 평문 refresh DB 저장, 모르는 로그인 id NPE, `anyRequest().permitAll()`, 기본 시크릿, 스택 트레이스 노출, SameSite 없는 쿠키.

## 아직 검증하지 못한 것 (구현할 때 공식 문서·테스트로 확인)
`ResponseEntityExceptionHandler`의 ProblemDetail 동작, Testcontainers 2.x의 Redis/`@ServiceConnection`, Boot 4의 Flyway 스타터 이름, Jackson 3의 unknown-property 설정 이름, MySQL 제약 이름 형식.

---

## 2차: meerkatgram 폴더 반영 (2026-11-06)

`meerkatgram-system`(v2 MSA + k8s 설정), `msa4-meerkatgram`(단일 앱), `msa-meerkatgram-client`(Vue)에서 가져온 것.

| 항목 | 출처 | 반영 문서 |
|---|---|---|
| 커밋 SHA 태그로 GHCR 푸시(선택 과제), 일반/비밀 값 분리 | meerkatgram-system | architecture 7장 |
| Vue Nginx 컨테이너 + `/api` 프록시 | client Dockerfile/nginx.conf | architecture 7장 |
| prod 설정 값(Hikari, 로그 레벨, 에러 숨김, Swagger 끔, TZ) | post `application-prod.yaml` | INTEGRATION |
| Swagger 에러 예시 자동 생성 `@ApiErrorCodes` | msa4-meerkatgram `@CustomApiResponse` | API_SPEC, CODE_CONVENTION |
| 에러 코드 표 ↔ enum 일치 테스트 | 그 프로젝트의 "문서-코드 불일치" 기술부채에서 착안 | API_SPEC, CODE_CONVENTION, ROADMAP |
| local 전용 시드 데이터 | `dummy/*.sql` (dev always / prod never) | INTEGRATION, ROADMAP |
| `.editorconfig`(줄바꿈은 lf로 변경) | client `.editorconfig` | CODE_CONVENTION |
| 프론트: 검증 규칙 함수 구조, 라우트 meta 가드, 에러 상태 스토어 | client | CODE_CONVENTION 2장 |
| count 쿼리 부담 메모 | 그 프로젝트 주석 | API_SPEC |

**가져오지 않은 것**: URL 블랙리스트 방식 인가(`anyRequest().permitAll()`), 가드마다 재발급 시도, 오류를 삼키는 `reissue`/업로드(null 반환), 파일 로깅, prod `show-sql: true`, 삭제 후 즉시 S3 삭제(롤백 시 불일치), 요청 만료 5분 전 선제 재발급(`jwt-decode` 의존성 추가, 단일 재발급 규칙과 충돌 가능).

**범위 결정 (2026-11-06)**: 이 프로젝트는 v1(단일 앱)만 한다. MSA 분리, Kafka, Gateway, Kubernetes/ArgoCD(GitOps)는 docs에서 제거했다. 6단계는 단일 앱의 Docker 전체 모드 + Swagger까지다.
