# 작업일지 — 레시피 사이트 (my-recipe-v1)

> 백엔드 개념(Java 21, Spring Boot, JPA, JWT, Redis, Docker, CI/CD, AWS S3)을 직접 만들며 익히는 개인 학습 프로젝트.
> 문제 해결 과정은 같은 폴더의 `TROUBLESHOOTING.md`, 단계별 할 일은 `docs/ROADMAP.md` 참고.

## 현재 위치

- **0단계(개발 환경 준비) 마무리 중**
- 끝난 것: 기획·설계 문서, 프로젝트 생성, 의존성, `.env`, JDK 21 설치, Docker(MySQL·Redis), 테스트 통과, bootRun 성공
- 남은 것: Git·GitHub·CI → ROADMAP 체크

---

## 2026-10-01 ~ 10-02 · 기획과 설계

### 기획
- "만개의 레시피" 같은 레시피 공유 사이트를 목표로 기능을 정리했다.
- 처음에는 AI 냉장고 인식, 식단·장보기, 시세 원가 같은 기능까지 검토했지만, **목적이 "공부 중인 개념을 직접 익히기"**라서 MVP를 4개로 줄였다.
  - 회원가입/로그인
  - 레시피 등록·수정·삭제 (제목, 사진, 재료, 조리 순서)
  - 좋아요·댓글
  - 재료로 검색 (예: "두부")
- Kafka, Kubernetes, MSA는 서비스가 여러 개일 때 의미가 있어서 MVP에서 빼고 확장 과제로 뒀다.

### 기술 스택
- 백엔드: Java 21, Spring Boot 4.1.x, Gradle(Groovy), JPA, Spring Security + JWT, Lombok
- 프론트: Vue 3 + JavaScript + Vite + Pinia + axios
- 데이터: MySQL 8.4, Redis 7.4
- 실행·배포: Docker Compose, GitHub Actions

### 주요 설계 결정
| 결정 | 선택 | 이유 |
|---|---|---|
| 서버 구조 | 모놀리스 1개, 도메인별 패키지 (member, recipe, ingredient, like, comment, global) | 학습 목적, 작은 기능 |
| 인증 | Access Token 30분(응답 본문, 메모리 보관) + Refresh Token 14일(httpOnly 쿠키) | 탈취 피해 최소화 + 로그인 유지 |
| Refresh Token 저장 | Redis `refresh:{memberId}`에 해시 저장, TTL 14일, 재발급마다 교체(로테이션), 재사용 감지 | TTL 자동 만료가 편하고 빠름. Redis는 보안 도구가 아니라 편의·성능 선택 |
| 재료 | 별도 테이블 + 다대다 연결 | 재료로 검색하려면 텍스트가 아니라 행이어야 함 |
| 좋아요 수 | `like_count` 컬럼 + DB에서 원자적 증가, `(member_id, recipe_id)` UNIQUE | 동시성 학습 |
| 사진 업로드 | Presigned URL로 브라우저가 저장소에 직접 업로드, DB에는 키만 | 서버가 파일을 다루지 않음 |
| API 문서 | `API_SPEC.md` + `openapi.yaml` (Swagger) | 설계와 구현 비교 |

### 작성한 문서 (`docs/`)
- `recipe_architecture.md` — 목적, 학습 목표, 품질 속성, 설계 결정, 흐름, 확장 과제
- `recipe_ERD_v1.md` — 테이블 7개, Redis·S3 저장 데이터
- `API_SPEC.md`, `openapi.yaml` — API 16개, 에러 코드
- `INTEGRATION.md` — 개발 환경, 인증 연동, 에러 처리, 사진 업로드, 체크리스트
- `CODE_CONVENTION.md` — 패키지, 이름, 계층별 규칙, 보안, 테스트, Git
- `ROADMAP.md` — 0~7단계 체크리스트
- 루트 `AGENTS.md`, `CLAUDE.md` — AI 도구 사용 규칙

### 0단계 시작
- Spring Initializr로 `myrecipe_backend/` 생성 (Group `com`, Package `com.myrecipe`, Jar, YAML, Java 21)
- `application.yaml`을 환경 변수 기반으로 교체, 루트 `.env`를 `spring.config.import`로 읽게 함
- `docker-compose.yml`, `.env.example`, `.gitignore`, `ci.yml` 작성
- 의존성 추가, Lombok 설정 수정 → TROUBLESHOOTING 1

---

## 2026-10-02 · 설계 문서 최종 검토

### 바로 고친 오류
- Dockerfile에서 명령 뒤에 붙인 주석(`USER app  # ...`)은 인자로 읽혀 빌드가 실패함 → 주석을 별도 줄로
- 컨테이너 기본 시간대가 UTC → `TZ: Asia/Seoul` 추가
- 문서 표현 불일치 정리

### 모듈 순환 참조 문제 → 이벤트 + facade
- recipe ↔ like·comment가 서로를 부르는 구조였다. 상세 응답의 `likedByMe`·`commentCount`, 삭제 시 자식 데이터 정리 때문이었다.
- 결정: 의존은 **like·comment → recipe** 한 방향만.
  - 삭제: recipe가 `RecipeDeletedEvent`를 발행하고, like·comment가 `@EventListener`(같은 트랜잭션)로 정리
  - 상세 조회: `facade/RecipeDetailFacade`가 여러 Service를 모아 조립
- 이유: Spring 이벤트 학습, 나중에 Kafka·MSA로 이어지는 구조

### AI 작업 환경
- `AGENTS.md`를 루트로 옮기고 `CLAUDE.md`에서 불러오게 함
- Claude Code 출력 스타일을 **Learning**으로 설정 (`.claude/settings.local.json`, Git 제외)
  - 핵심 로직(인증·권한, 동시성, 검색 쿼리, 이벤트, 사진 검증)은 `TODO(human)`으로 남겨 직접 작성
- 카파시 가이드라인 중 "요청한 곳만 고치기", "성공 기준 먼저 정하기"를 반영

---

## 2026-10-06 · 보안 보완과 환경 설정

### 보안 결점 수정 ("보안 결점은 모두 고친다"로 결정)
| 문제 | 수정 |
|---|---|
| 만료된 Access Token 때문에 로그아웃 요청이 막힘 | JWT 필터가 `/api/auth/**`는 검사하지 않음 |
| 처리 안 된 에러가 401로 가려짐 | `/error` 허용, 스택 트레이스 비노출 유지 |
| 여러 탭이 동시에 재발급하면 재사용으로 오인돼 로그아웃 | 서버 규칙은 그대로, 프론트에서 Web Locks API로 탭 사이 재발급 직렬화 |
| Refresh Token을 Access Token 자리에 써도 통과 | JWT에 `type` 클레임(access/refresh) 추가, HS256 고정 |
| 비밀번호 무한 시도 가능 | 같은 이메일 5회 실패 시 15분 잠금 (Redis `INCR` + TTL), 429 `LOGIN_TOO_MANY_ATTEMPTS` |
| 응답 시간으로 가입 여부 노출 | 없는 이메일도 BCrypt 비교 1회 수행 |
| Content-Type만 이미지로 위장한 파일 | 파일 앞 12바이트(매직 넘버)로 실제 형식 확인 |
| XSS로 메모리 토큰 탈취 가능성 | `v-html` 금지, 토큰 `localStorage` 저장 금지, 보안 헤더 유지 |

- 받아들인 위험(MVP 범위 밖): 로그아웃 후 Access Token 최대 30분 유효, 회원가입 시 이메일 중복 응답, 로그인 잠금을 이용한 방해

### 환경 설정
- `.env` 비밀 값을 무작위 64자로 교체 → TROUBLESHOOTING 2
- Docker 역할(설정 자동화, 볼륨) 정리
- Docker Desktop 미실행 → TROUBLESHOOTING 3
- MinIO 이미지 401 → TROUBLESHOOTING 4, 5

---

## 2026-10-07 · 사진 저장소 결정과 JDK 설치

### 사진 저장소: MinIO → AWS S3
- MinIO 배포 중단으로 RustFS, SILO, 과거 버전 빌드를 비교한 끝에 **AWS S3 + CloudFront**로 결정 (TROUBLESHOOTING 5)
- `docker-compose.yml`에서 저장소 컨테이너 제거 → Docker는 MySQL·Redis만
- `.env`·`.env.example`의 `STORAGE_*`를 AWS용 5개로 변경 (리전, 버킷, CloudFront 주소, 접속 키 2개)
- `infra/aws/`에 IAM 정책, 버킷 정책(CloudFront OAC), CORS 설정 파일 추가
- AWS 계정 보안 절차를 5단계 체크리스트에 추가 (root MFA, 예산 알림, Push protection)
- 문서 9개를 S3 기준으로 수정, 결정 경위 기록

### JDK 21 설치
- `gradlew.bat`에서 Java를 찾지 못함 → Temurin 21 설치, `JAVA_HOME`·`Path` 등록 → TROUBLESHOOTING 6

### 테스트 실패: PC에 설치된 MySQL과 포트 충돌
- `gradlew test`가 `Access denied for user 'myrecipe'@'localhost'`로 실패 → PC의 MySQL(`mysqld`)이 3306을 쓰고 있었음
- PC MySQL은 그대로 두고 Docker MySQL을 **3307**로 옮김 (`docker-compose.yml`, `.env`, `.env.example`, INTEGRATION 포트 표)
- `BUILD SUCCESSFUL` 확인 → TROUBLESHOOTING 7

### bootRun 실패: 로컬 Kubernetes가 8080·5173 사용
- Docker Desktop Kubernetes의 NGINX Gateway(예전 실습)가 8080·5173을 차지 → Spring이 뜨지 못함
- 포트를 8081·5174로 옮기는 안을 검토했다가 되돌리고, **Kubernetes를 끄는 쪽**으로 결정. 남은 envoy 컨테이너 직접 삭제
- `bootRun` 성공 (`Tomcat started on port 8080`) → TROUBLESHOOTING 8
- 이 PC 포트 정리: Spring 8080, Vue 5173, MySQL 3307, Redis 6379. 프로젝트 실행 시 Kubernetes는 끈다

### 결정: IntelliJ만이 아니라 터미널에서도 Java가 동작하게 한다
IntelliJ의 JDK만으로도 IDE 안에서 실행·빌드는 된다. 그래도 PC에 JDK를 설치하고 `JAVA_HOME`·`Path`까지 등록한 이유는 다음과 같다.

| 이유 | 내용 |
|---|---|
| Claude Code가 터미널로 일함 | 1단계부터 Claude Code(Learning 스타일)를 쓰는데, 테스트·실행을 IntelliJ 버튼이 아니라 `./gradlew test` 같은 터미널 명령으로 한다. 터미널에 Java가 없으면 "테스트를 직접 실행해 통과해야 커밋" 규칙을 지킬 수 없다 |
| IntelliJ JDK도 비어 있었음 | `.jdks\temurin-21.0.11` 폴더가 비어 있어 IntelliJ 쪽도 정상이 아니었을 가능성이 컸다. PC에 하나 설치하고 IntelliJ도 그걸 가리키게 해서 **IDE와 터미널이 같은 Java**를 쓰게 했다 |
| 문서·CI가 명령어 기준 | ROADMAP·INTEGRATION·트러블슈팅이 `.\gradlew.bat ...` 명령으로 적혀 있고, GitHub Actions도 `./gradlew build`를 실행한다. 내 PC에서 같은 명령을 돌려야 "CI에서만 실패"를 미리 잡는다 |

| 상황 | IntelliJ JDK만 | 터미널 Java도 |
|---|---|---|
| IntelliJ ▶ 버튼, Gradle 탭 | ✅ | ✅ |
| Claude Code가 테스트 실행 | ❌ | ✅ |
| 문서의 명령어 그대로 따라 하기 | ❌ | ✅ |
| CI와 같은 방식으로 확인 | ❌ | ✅ |

- 참고: Docker 이미지 빌드(6단계)는 컨테이너 안의 JDK를 쓰므로 PC의 Java와 상관없다.
- 후속 작업: IntelliJ **File → Project Structure → Project → SDK**를 설치한 `jdk-21`(Eclipse Adoptium)로 맞춘다.

---

## 2026-10-08 · 저장소 구조 정리

- 백엔드는 IntelliJ로 `myrecipe_backend`만, 프론트는 VS Code로 `frontend`만 열기로 함 → **백엔드·프론트 Git 저장소 분리**
- `myrecipe_backend` 하나에 모두 모음: `.env`·`.env.example`·`docker-compose.yml`·`infra/aws/`·`.claude/`·`.github/`·`AGENTS.md`·`CLAUDE.md`, 문서는 `docs/`, 기록은 `notes/`
- `application.yaml`의 `.env` 경로를 `../.env` → `.env`로, compose는 `build: .`, CI의 `working-directory` 제거
- compose에 `name: my-recipe-v1`을 넣어 폴더를 옮겨도 컨테이너·볼륨 이름이 그대로 유지되게 함
- 바깥 `my-recipe-v1`의 남은 파일(.idea, 빈 폴더, 옛 .gitignore) 삭제
- `.claude/settings.json`에 `Read(./.env)` 금지 규칙 추가 (Claude Code가 `.env`를 못 읽게 강제)
- 첫 커밋 "기본 세팅" push. GitHub 저장소 이름 `myrecipe` → `myrecipe-backend`, `settings.gradle`의 프로젝트 이름도 `myrecipe-backend`로 변경

## 다음 할 일

### 0단계 마무리
- [x] `docker compose down -v` → `docker compose up -d` → mysql `healthy`, redis `Up` 확인
- [x] `.\gradlew.bat bootRun` → `Started MyrecipeApplication` 확인
- [x] `.\gradlew.bat test` → `BUILD SUCCESSFUL`
- [x] `ci.yml`을 `.github/workflows/`로 이동
- [ ] GitHub 저장소 생성, Secret scanning·Push protection 켜기, push, Actions 초록불
- [ ] `ROADMAP.md` 0단계 체크, 기록 표 작성
- [x] 빈 `docker/storage` 폴더 삭제

### 1단계 (공통 기반 + 회원가입/로그인)
- `global`: `BaseTimeEntity` → `ErrorCode` → `BusinessException` → `ErrorResponse` → `GlobalExceptionHandler`
- 이후 회원가입·로그인·JWT 필터·Refresh Token·로그인 잠금

### 결정 보류
- 로그아웃한 Access Token 즉시 차단(Redis 블랙리스트)을 MVP에 넣을지
