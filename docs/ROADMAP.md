# 구현 로드맵

> 기준일: 2026-10-02
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

나머지 라이브러리는 필요한 단계에서 직접 추가한다: JWT(1단계), AWS SDK S3(5단계), springdoc-openapi(6단계).

## 1단계. 공통 기반 + 회원가입/로그인

- [ ] `global`: BaseTimeEntity, ErrorCode, BusinessException, 전역 예외 처리, 에러 응답 형식
- [ ] 회원가입(BCrypt), 로그인(Access Token만), JWT 필터, `/api/members/me`
- [ ] JWT `type` 클레임(access/refresh) 확인, JWT 필터가 `/api/auth/**`는 건너뜀, `/error` 허용
- [ ] Refresh Token + Redis: 재발급(로테이션), 로그아웃, 재사용 감지
- [ ] 로그인 실패 5회 → 15분 잠금 (Redis `INCR` + TTL), 없는 이메일도 BCrypt 비교
- [ ] 필터 단계 에러(401)도 API_SPEC 형식으로 응답

## 2단계. 레시피 CRUD (사진 없이)

- [ ] 레시피·조리 순서·재료 엔티티와 연관관계
- [ ] 등록·상세·수정(목록 통째 교체)·삭제, 작성자 권한 확인
- [ ] 삭제 시 `RecipeDeletedEvent` 발행 (리스너는 4단계에서 붙음)
- [ ] `facade/RecipeDetailFacade`로 상세 응답 조립 (이 단계에서는 `likedByMe=false`, `commentCount=0`)
- [ ] 목록 조회에서 N+1을 직접 확인하고 fetch join으로 해결

## 3단계. 재료로 검색

- [ ] `?ingredient=두부` 검색, 페이징, 최신순

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

- [ ] Presigned URL 발급, 레시피 저장 시 객체 확인(키 소유·존재·크기·형식·파일 앞부분 바이트)
- [ ] 레시피 삭제·사진 교체 시 커밋 후 객체 삭제

## 6단계. Docker 전체 모드 + Swagger

- [ ] `Dockerfile`, `.dockerignore` (저장소 루트)
- [ ] `docker compose --profile app up -d --build`로 전체 실행
- [ ] springdoc으로 Swagger UI 생성, `openapi.yaml`과 비교
- [ ] CI에 Docker 이미지 빌드 추가

## 7단계. Vue 화면

- [ ] 로그인 유지(앱 시작 시 재발급), 자동 재발급 인터셉터, Web Locks로 탭 사이 재발급 한 번씩
- [ ] 목록·검색·상세·작성·수정 화면, 사진 업로드
- [ ] `INTEGRATION.md` 체크리스트 전체 통과

## 기록

| 날짜 | 단계 | 어려웠던 점 / 새로 이해한 것 |
|---|---|---|
| 2026-10-08 | 0단계 | MinIO 배포 중단 → 사진 저장소를 AWS S3로 변경. PC MySQL(3306)·로컬 Kubernetes(8080) 포트 충돌을 원인 추적으로 해결. 오픈소스는 "누가 관리하는가"도 본다 |
