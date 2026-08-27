# 릴리스

`CLAUDE.md` 에서 빼낸 문서다. 세션마다 읽히지 않으므로 **릴리스할 때 여기를 연다.**

설치 좌표는 `io.github.jjh75607:query-counter:0.5.0` 이다. `0.1.0` 이하는 Central 에 없다.
JitPack 으로 내던 시절의 것이고 2026-08-13 에 그 경로를 접었다. 근거는 태그 7개 중 JitPack 이
빌드한 것이 둘뿐이었다는 것이다. JitPack 은 요청받을 때만 빌드하므로 나머지는 아무도 받아간
적이 없다는 뜻이다. 쓰는 사람이 없는데 배포 경로가 둘이면 서명 설정과 문서와 검증이 모두
두 벌이 된다.

절차는 이렇다.

1. `build.gradle` 의 `version` 을 올리고 `CHANGELOG.md` 를 정리한다
2. 태그와 GitHub 릴리스를 만든다. 제목은 태그와 정확히 같게 쓴다 (`v0.2.0`. `v.0.2.0` 처럼
   점이 끼지 않게)
3. `release.yml` 이 자동으로 돌아 서명한 아티팩트를 Central 에 올린다. 태그와
   `build.gradle` 의 `version` 이 다르면 여기서 멈춘다. Central 은 한 번 올라간 버전을
   덮어쓸 수 없어서 올리기 전에 걸러야 한다
4. `https://central.sonatype.com/publishing/deployments` 에서 확인하고 Publish 를 누른다.
   이걸 누르기 전에는 아무도 받을 수 없다
5. `verify-release.yml` 을 태그를 넣어 수동 실행한다. 공개를 누른 뒤에 도는 것이라 자동
   실행은 걸어두지 않았다. 명령으로는 `gh workflow run verify-release.yml -f tag=v0.5.0` 이다

**Publish 를 누른 뒤 `repo1.maven.org` 에 퍼지기까지 몇 분에서 수십 분 걸린다.** 4번과 5번
사이의 이 시차를 모르면 5번의 404 를 릴리스 실패로 읽는다. `0.4.0` 에서 실제로 그랬다.
그래서 5번은 최대 15분까지 기다려 보고, 그래도 없으면 무엇을 확인해야 하는지 함께 낸다.

**발행 검증은 두 층이다.** CI 의 `smoke` 잡이 매 변경에서 로컬 저장소로 낸 SNAPSHOT 을 소비자
프로젝트가 받아 호출한다. `verify-release.yml` 은 릴리스 뒤에 Central 의 실물을 본다. 앞엣것이
없던 동안 `0.2.0` 이 설치 불가 상태로 나갔다. **릴리스 뒤에만 도는 검증은 늦다.**

**`build.gradle` 의 `version` 이 유일한 출처다.** 태그는 거기에 `v` 를 붙인 것이고,
워크플로가 둘이 같은지 검사한다. Central 버전에는 `v` 가 없다.

### 의존성 버전은 반드시 적는다

`build.gradle` 의 `springBootFloor`, `springFrameworkFloor`, `junitFloor` 는 **발행물에
그대로 실려 사용자에게 나가는 값**이다. 지원 하한인 Spring Boot 3.0.0 이 쓰는 버전이다.

버전을 비워두고 BOM 으로 채우는 방식은 쓰지 않는다. 그 방식은 우리가 빌드할 때만 값을
채우고 발행물에는 빈칸을 남긴다. 받는 쪽은 몇 번을 받을지 몰라 `Could not find
org.springframework:spring-test:` 로 멈춘다. `0.2.0` 이하가 전부 이 상태로 나갔고
아무도 설치할 수 없었다. 이슈 98 이다.

하한이어야 하는 이유는, 사용자가 이미 더 높은 Spring Boot 를 쓰고 있으면 그쪽이 이겨야
하기 때문이다. 우리가 빌드한 버전을 적으면 남의 프로젝트 버전을 끌어올린다.

CI 가 `-PspringBootVersion` 으로 지원 범위를 검증하는 것은 `enforcedPlatform` 이 맡는다.
`compileOnly`, `annotationProcessor`, `testImplementation` 에만 걸어서 발행물에 새지 않게
했다. `io.spring.dependency-management` 플러그인으로는 이게 안 된다. 그 플러그인은 명시한
버전을 덮어쓰지 못한다.

### 서명

Central 은 서명 없는 아티팩트를 받지 않는다. 서명 키는 저장소 시크릿 `SIGNING_KEY` 와
`SIGNING_PASSWORD` 에, 포털 사용자 토큰은 `MAVEN_CENTRAL_USERNAME` 과
`MAVEN_CENTRAL_PASSWORD` 에 있다.

`signAllPublications()` 는 버전이 `-SNAPSHOT` 으로 끝나지 않으면 서명을 필수로 만든다.
그래서 **서명 키 없이는 `publishToMavenLocal` 도 실패한다.** 로컬에서 발행을 시험하려면
`-PsigningInMemoryKey` 와 `-PsigningInMemoryKeyPassword` 로 키를 넘긴다.

`-Pversion=0.3.0-SNAPSHOT` 으로 서명을 건너뛰려는 시도는 통하지 않는다. `build.gradle` 이
`version` 을 직접 대입하고 있어서 명령줄 프로퍼티가 덮이지 않는다.

그래서 그 자리를 `-PpublishVersion` 으로 열어 두었다. `version = findProperty('publishVersion') ?: '0.5.0'`
이고, SNAPSHOT 을 넘기면 `signMavenPublication` 이 SKIPPED 되어 키 없이
`publishToMavenLocal` 이 된다. **소비자 스모크 테스트 전용이다.**

**릴리스에는 쓰지 않는다.** `release.yml` 은 이 프로퍼티를 넘기지 않고 리터럴 기본값을 읽어
태그와 대조하므로, 이걸로 발행하면 그 검사를 우회하게 된다. 리터럴이 여전히 유일한 출처다.
