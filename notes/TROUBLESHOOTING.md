# 트러블슈팅 기록 — 레시피 사이트 (my-recipe-v1)

> 0단계(개발 환경 준비) 중 겪은 문제와 해결 과정. 형식: 증상 → 원인 → 해결 → 배운 점

---

## 1. 생성한 프로젝트의 build.gradle에 의존성이 없음

**날짜**: 2026-10-02 · **분류**: Spring Boot / Gradle

### 증상
- Spring Initializr로 만든 `build.gradle`에 `spring-boot-starter`, devtools, 테스트 의존성만 있었다.
- Web, JPA, Security 등 선택했다고 생각한 의존성이 빠져 있었다.

### 원인
- Initializr에서 의존성을 고르지 않은 채 생성했다.

### 해결
- IntelliJ `build.gradle`에서 `Alt + Insert` → **Edit Starters**로 추가했다.
- 추가한 것: Spring Web, Spring Data JPA, MySQL Driver, Spring Security, Validation, Spring Data Redis, Lombok.
- Lombok이 `implementation`으로 들어가 있어서 아래처럼 바꿨다.

```groovy
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'
```

### 배운 점
- Lombok은 **컴파일할 때** 코드를 만들어 주는 도구라서 `annotationProcessor`로 등록해야 동작한다. 실행 파일(jar)에는 필요 없다.
- Spring Boot 4는 3.x와 스타터 이름이 일부 다르다(예: `spring-boot-starter-webmvc`). 이름을 외워서 쓰지 말고 Initializr나 Edit Starters로 추가한다.

---

## 2. `.env` 생성 스크립트가 "이미 있어요"라며 아무것도 바꾸지 않음

**날짜**: 2026-10-06 · **분류**: 환경 변수 / 보안

### 증상
- 비밀 값을 무작위로 채워 `.env`를 만드는 PowerShell 스크립트를 실행했다.
- `.env가 이미 있어요. 덮어쓰지 않았어요.`만 나오고 값이 바뀌지 않았다.

### 원인
- 며칠 전 `.env.example`을 복사해 둔 `.env`가 이미 있었다.
- 그런데 비밀 값 6개가 `change-me` 예시 값 그대로였다.
- 스크립트는 실수로 덮어쓰는 걸 막으려고 파일이 있으면 멈추게 만들어져 있었다.

### 해결
- 기존 파일은 그대로 두고, **값이 `change-me`로 시작하는 줄만** 바꾸는 스크립트를 따로 실행했다.

```powershell
function New-Secret {
    $b = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    -join ($b | ForEach-Object { $_.ToString('x2') })
}
$c = [IO.File]::ReadAllText("$PWD\.env")
foreach ($k in 'DB_PASSWORD','DB_ROOT_PASSWORD','REDIS_PASSWORD','JWT_SECRET') {
    $c = $c -replace "(?m)^$k=change-me[^\r\n]*", "$k=$(New-Secret)"
}
[IO.File]::WriteAllText("$PWD\.env", $c)
```

- 확인: `Select-String -Path .env -Pattern "change-me"` 결과가 비어 있으면 성공이다.

### 배운 점
- 비밀 값은 `Get-Random`이 아니라 **보안용 난수 생성기**(`RandomNumberGenerator`)로 만든다.
- 16진수로 만들면 `+ / =` 같은 특수문자가 없어서 설정 파일 파싱 문제가 없다.
- PowerShell 5의 `Set-Content -Encoding UTF8`은 파일 맨 앞에 BOM을 붙인다. docker compose가 첫 줄을 잘못 읽을 수 있어서 `[IO.File]::WriteAllText`(BOM 없는 UTF-8)로 저장했다.
- **MySQL 비밀번호는 처음 `docker compose up` 할 때 굳는다.** 그 뒤에 `.env`를 바꿨다면 `docker compose down -v`로 볼륨을 지워야 새 비밀번호가 적용된다.

---

## 3. `docker` 명령이 Docker API에 연결하지 못함

**날짜**: 2026-10-06 · **분류**: Docker

### 증상

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine;
check if the path is correct and if the daemon is running
```

### 원인
- Docker Desktop이 실행되지 않은 상태였다.
- Windows에서는 Docker Desktop이 WSL2로 리눅스 환경을 띄우고 그 안에서 컨테이너를 돌린다. 꺼져 있으면 `docker` 명령이 연결할 엔진이 없다.

### 해결
- Docker Desktop을 실행하고, 왼쪽 아래 **Engine running** 표시가 뜬 뒤 다시 명령을 실행했다.

### 배운 점
- `docker` 명령은 "클라이언트"이고 실제 일은 Docker 엔진(데몬)이 한다. 엔진이 꺼져 있으면 명령 자체는 있어도 동작하지 않는다.

---

## 4. `docker compose logs minio-init`이 아무것도 출력하지 않음

**날짜**: 2026-10-06 · **분류**: Docker

### 증상
- `docker compose up -d` 후 초기 설정 컨테이너의 로그를 봤는데 빈 화면이었다.

### 원인
- 컨테이너가 **아예 만들어지지 않았다.** 실행됐다면 성공이든 실패든 로그가 한 줄은 남는다.
- 앞 단계인 `docker compose up -d`가 이미지 다운로드에서 실패해 중간에 멈춘 상태였다(5번 문제).

### 해결
- 원인을 찾기 위해 아래 두 명령을 썼다.

```powershell
docker compose ps -a          # 종료된 컨테이너까지 상태 확인
docker compose up minio-init  # -d 없이 실행해 진행 과정과 에러를 화면에 바로 출력
```

### 배운 점
- 로그가 비어 있으면 "컨테이너 안의 문제"가 아니라 "컨테이너가 시작조차 안 된 문제"를 먼저 의심한다.
- `docker compose ps`는 실행 중인 것만 보여준다. 종료된 컨테이너는 `-a`를 붙여야 보인다.

---

## 5. MinIO 이미지를 받을 수 없음 (401 Unauthorized)

**날짜**: 2026-10-06 ~ 10-07 · **분류**: Docker / 기술 선택

### 증상

```text
Error response from daemon: unknown: failed to resolve reference "quay.io/minio/mc:latest":
unexpected status from HEAD request to https://quay.io/v2/minio/mc/manifests/latest: 401 Unauthorized
```

### 원인
- 설정 문제가 아니라 **MinIO 회사의 배포 정책 변경** 때문이었다.

| 시기 | 변화 |
|---|---|
| 2021 | 라이선스를 AGPL로 변경 |
| 2025-05 | 무료 버전 웹 콘솔에서 관리 기능 제거 |
| 2025-10 | 무료 버전 바이너리·Docker 이미지 배포 중단 |
| 2026 상반기 | GitHub 저장소 보관(archived) |
| 2026-09 | Docker Hub `minio/minio`, `minio/mc` 삭제, quay.io 이미지도 로그인 없이 받을 수 없음(401) |

### 검토한 대안

| 대안 | 장점 | 문제 |
|---|---|---|
| RustFS | Apache 2.0, MinIO 대체 목표, 활발한 개발 | 1.0이 2026-09 출시. 새 코드라 숨은 버그·자료 부족 |
| SILO (MinIO 포크, Pigsty) | MinIO 코드 그대로 + 보안 패치, 이미지 서명·SBOM | 작은 조직이 유지. 장기 유지 불확실 |
| MinIO 과거 버전 직접 빌드 | 출처 확실 | 보안 패치 없음, 알려진 취약점 그대로 |
| bitnamilegacy/minio | Bitnami라 출처는 믿을 만함 | 2025-08 갱신 중단, 패치 없음 |
| 비공식 복사본 | 바로 사용 가능 | 출처 검증 불가 (공급망 위험) |
| **AWS S3 직접 사용** ✅ | 관리 주체 확실, 실무 표준 학습 | 인터넷 필요, 키 유출 시 요금 위험 → 보안 설정 필수 |

### 해결
- 로컬 저장소 컨테이너를 빼고 **AWS S3 + CloudFront**를 쓰기로 했다.
- 앱은 처음부터 **AWS SDK(S3 API)**로만 접속하도록 설계해서 코드 변경은 없다. `docker-compose.yml`, `.env`, 문서만 바꿨다.
- 보안 구성: 버킷 비공개(퍼블릭 액세스 차단 유지), CloudFront OAC로만 읽기, 앱 전용 IAM 사용자(`recipes/*`만), CORS는 `localhost:5173`의 `PUT`만, root MFA, 예산 알림, GitHub Push protection.

### 배운 점
- 오픈소스를 고를 때는 기능뿐 아니라 **"누가 관리하고, 앞으로도 무료로 쓸 수 있는가"**를 본다.
- 특정 제품 전용 SDK 대신 **표준 API(S3)**로 코드를 짜 두면 저장소를 바꿔도 코드가 안 바뀐다.
- Docker 이미지는 `latest` 대신 버전을 고정하고, 가능하면 다이제스트(`@sha256:...`)로 고정한다.
- 비공식 이미지는 "누가, 어떤 소스로 빌드했는지" 검증할 수 있는지(서명, SBOM, 빌드 증명)를 본다.

---

## 6. `gradlew.bat` 실행 시 Java를 찾을 수 없음

**날짜**: 2026-10-07 · **분류**: Java / Windows 환경 변수

### 증상

```text
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
ERROR: JAVA_HOME is set to an invalid directory
```

```text
java : 'java' 용어가 cmdlet, 함수, 스크립트 파일 또는 실행할 수 있는 프로그램 이름으로 인식되지 않습니다.
```

- `Test-Path "$env:JAVA_HOME\bin\java.exe"` 결과가 `False`였다.

### 원인
- PC에 쓸 수 있는 JDK가 없었다.
- IntelliJ가 JDK를 받아 두는 폴더(`%USERPROFILE%\.jdks\temurin-21.0.11`)가 **비어 있었다.** 다운로드가 중간에 실패한 것으로 보인다.
- `JAVA_HOME`도 잘못된 경로를 가리키고 있었다.
- `$jdk = "경로"`처럼 PowerShell 변수에 넣기만 하고 환경 변수로 등록하지 않았다.

### 해결
1. Temurin JDK 21 설치

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

2. 사용자 환경 변수 등록 + 현재 창에도 바로 적용

```powershell
$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory |
       Where-Object { $_.Name -like "jdk-21*" } |
       Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName

[Environment]::SetEnvironmentVariable("JAVA_HOME", $jdk, "User")
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($userPath -notlike "*$jdk\bin*") {
    [Environment]::SetEnvironmentVariable("Path", "$jdk\bin;$userPath", "User")
}
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"
java -version
```

3. IntelliJ도 **File → Project Structure → Project → SDK**를 같은 JDK로 맞춘다.

### 배운 점
- **IntelliJ의 JDK 설정과 터미널의 Java는 별개다.** IntelliJ 안에서는 되는데 PowerShell에서는 안 될 수 있다.
- 환경 변수는 **새로 연 창부터** 적용된다. 이미 열린 창은 `$env:`로 직접 넣어 줘야 한다. IntelliJ 터미널은 IntelliJ를 재시작해야 한다.
- `.env` 파일과 `JAVA_HOME`은 다르다. `.env`는 Spring 앱이 **실행된 뒤** 읽고, `JAVA_HOME`은 Gradle이 **실행되기 전** 필요하다.
- 확인 3종 세트: `$env:JAVA_HOME`, `java -version`, `Test-Path "$env:JAVA_HOME\bin\java.exe"`.
- IntelliJ만으로도 개발은 되지만, Claude Code·문서·CI가 모두 터미널 명령(`gradlew`)을 쓰기 때문에 터미널 Java가 필요하다. 자세한 이유는 작업일지(`Work Log_1st_weeks.md`) 2026-10-07 "결정" 항목 참고.

---

## 7. 테스트(contextLoads)가 MySQL 접속 거부로 실패

**날짜**: 2026-10-07 · **분류**: MySQL / Docker / 포트 충돌

### 증상
- `.\gradlew.bat test` 실행 시 `contextLoads()`가 실패했다.
- 화면에는 원인이 잘 보이지 않는 에러만 나왔다.

```text
Caused by: org.hibernate.HibernateException at DialectFactoryImpl.java:190
```

- 테스트 결과 파일(`build/test-results/test/*.xml`)에서 진짜 원인을 찾았다.

```text
Caused by: java.sql.SQLException: Access denied for user 'myrecipe'@'localhost' (using password: YES)
```

### 원인
- **PC에 직접 설치된 MySQL이 3306 포트를 쓰고 있었다.**
- Spring은 `localhost:3306`으로 접속했는데, Docker MySQL이 아니라 PC의 MySQL이 응답했다. PC MySQL에는 `myrecipe` 계정이 없어서 거부됐다.
- 단서: 에러의 `'myrecipe'@'localhost'`. Docker MySQL로 접속하면 보통 `@'172.x.x.x'` 같은 Docker 내부 주소로 찍힌다.
- `Unable to determine Dialect`는 DB에 못 붙어서 연쇄로 난 에러였다. Hibernate가 DB 종류를 알아내려면 먼저 접속해야 한다.

### 확인 방법

```powershell
# 3306 포트를 누가 쓰고 있는지
Get-NetTCPConnection -LocalPort 3306 -State Listen |
    ForEach-Object { Get-Process -Id $_.OwningProcess } | Select-Object Id, ProcessName
```

- 결과가 `mysqld`면 PC에 설치된 MySQL이다. Docker라면 `com.docker.backend`나 `wslrelay`가 보인다.

### 해결
- PC의 MySQL은 다른 용도로 쓸 수 있으니 끄지 않고, **Docker MySQL을 3307 포트로 옮겼다.**

```yaml
# docker-compose.yml
ports:
  - "127.0.0.1:3307:3306"   # 내 PC 3307 → 컨테이너 3306
```

```properties
# .env
DB_URL=jdbc:mysql://localhost:3307/myrecipe?...
```

- `docker compose down -v` → `docker compose up -d` → `.\gradlew.bat test` → `BUILD SUCCESSFUL`

### 배운 점
- 화면에 보이는 마지막 에러보다 **가장 안쪽의 `Caused by`**가 진짜 원인인 경우가 많다. Gradle 테스트의 자세한 로그는 `build/test-results/test/*.xml`이나 `build/reports/tests/test/index.html`에 있다.
- 포트는 "먼저 차지한 프로그램"이 받는다. 같은 포트를 쓰는 프로그램이 둘이면, 내가 생각한 쪽이 아닌 곳으로 접속할 수 있다.
- `호스트포트:컨테이너포트`에서 앞쪽만 바꾸면 된다. 컨테이너끼리 통신(`mysql:3306`)과 CI는 그대로다.
- DB 도구(IntelliJ Database 등)로 Docker MySQL에 접속할 때도 이제 **3307**을 쓴다.

---

## 8. bootRun이 바로 종료됨 (8080 포트를 로컬 Kubernetes가 사용)

**날짜**: 2026-10-07 · **분류**: 포트 충돌 / Docker Desktop Kubernetes

### 증상
- 테스트는 통과했는데 `.\gradlew.bat bootRun`만 실패했다.

```text
> Task :bootRun FAILED
Process 'command '...\bin\java.exe'' finished with non-zero exit value 1
```

- 이 메시지는 "앱이 에러로 끝났다"는 결과일 뿐이고, 진짜 원인은 그 위쪽 로그에 있다.

### 원인 추적

```powershell
# 1) 8080을 누가 쓰는지
Get-NetTCPConnection -LocalPort 8080 -State Listen |
    ForEach-Object { Get-Process -Id $_.OwningProcess } | Select-Object Id, ProcessName
# → wslrelay, com.docker.backend  (= Docker 컨테이너가 쓰는 중)

# 2) 어떤 컨테이너인지
docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}"
# → kindccm-... (envoyproxy/envoy)  0.0.0.0:5173->5173, 0.0.0.0:8080->8080
#   desktop-control-plane, desktop-worker ... (kind 노드)

# 3) Kubernetes 안의 어떤 서비스인지
kubectl get svc -A
# → nginx-gateway-fabric / gateway-nginx  (LoadBalancer)
```

- **Docker Desktop의 Kubernetes**가 켜져 있었고, 예전 실습에서 만든 **NGINX Gateway Fabric의 Gateway**가 8080·5173 리스너로 남아 있었다.
- Kubernetes의 LoadBalancer 서비스를 PC 포트로 열어 주는 envoy 컨테이너(`kindccm-...`)가 두 포트를 차지했다.
- 5173은 나중에 Vue(Vite)가 쓸 포트라 7단계에서도 같은 문제가 생길 뻔했다.

### 검토한 해결책

| 방법 | 내용 | 결과 |
|---|---|---|
| 컨테이너 `docker stop` | envoy 컨테이너만 멈추기 | ❌ Kubernetes가 관리해서 다시 살아남 |
| 서비스 `kubectl delete svc` | `gateway-nginx` 삭제 | ❌ Gateway 컨트롤러가 다시 만듦. 지우려면 Gateway 리소스를 지워야 함 |
| 우리 포트 변경 | Spring 8081, Vite 5174 | 가능하지만 설정·문서 여러 곳을 바꿔야 함 → 되돌림 |
| **Kubernetes 끄기** ✅ | Docker Desktop → Settings → Kubernetes → 체크 해제 | 채택. 지금은 K8s 실습을 안 해서 가장 단순함 |

### 해결
1. Docker Desktop에서 Kubernetes를 껐다.
2. 관리자(`kind-cloud-provider`)가 먼저 내려가면서 envoy 컨테이너 하나가 정리되지 않고 남았다. 관리하는 쪽이 없으니 직접 지웠다.

```powershell
docker rm -f kindccm-<ID>
```

3. `.\gradlew.bat bootRun` → `Tomcat started on port 8080`, `Started MyrecipeApplication` 확인

### 배운 점
- `wslrelay`·`com.docker.backend`가 포트를 쓰고 있으면 **Docker 컨테이너**가 범인이다. `docker ps`의 PORTS 칸으로 찾는다.
- Kubernetes가 만든 컨테이너·서비스는 직접 지워도 **상위 리소스(컨트롤러, Gateway)**가 다시 만든다. 원인 리소스를 찾아서 정리한다.
- Docker Desktop의 Kubernetes는 꺼 두지 않으면 백그라운드에서 포트와 메모리를 계속 쓴다. 실습할 때만 켠다.
- 이 PC의 포트 정리: Spring **8080**, Vue **5173**, MySQL **3307**(PC MySQL이 3306 사용), Redis 6379. 이 프로젝트를 돌릴 때는 Kubernetes를 끈다.
