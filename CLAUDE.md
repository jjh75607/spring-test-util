# query-counter

Hibernate로 실행되는 쿼리 수와 실행 시간을 테스트에서 검증하는 라이브러리.
Maven Central 로 배포한다.

## 이 라이브러리가 반드시 지켜야 할 것

**활성화하지 않으면 사용자의 테스트에 아무 영향을 주지 않아야 한다.**

테스트 라이브러리이므로 이걸 붙였다는 이유로 남의 테스트가 깨지면 존재 의미가 없다.
이 성질은 문서가 아니라 `AutoConfigTest`가 보증한다. 자동 설정을 건드릴 때
그 테스트를 먼저 읽는다.

- `AutoConfig`는 `query-counter.enabled=true`일 때만 활성화된다. 기본값은 꺼짐
- SQL 로깅은 `query-counter.logging.enabled`로 따로 켠다. 카운팅과 무관한 기능이라
  기본값이 꺼짐이다. 항상 켜져 있으면 테스트가 많은 프로젝트에서 로그가 오염된다
- 자동 설정에서 `@ComponentScan`을 쓰지 않는다. 사용자의 스캔과 겹치고 순서에 따라
  결과가 달라진다. `@Bean`으로 명시한다
- **프록시가 DataSource 의 예외 타입을 바꾸지 않는다.** `Method.invoke` 는 대상이 던진 예외를
  `InvocationTargetException` 으로 감싸므로 `ProxyDataSourceInterceptor` 가 그것을 벗겨 다시
  던진다. 안 벗기면 호출자가 `SQLException` 대신 `UndeclaredThrowableException` 을 받고,
  스프링과 하이버네이트의 예외 변환이 통째로 건너뛰어진다. 공개 프로젝트에서 실제로 겪었다
  (`~/other/oss-contrib/query-counter-field-test/RESULTS-2026-08-27.md` 의 발견 6)

## 구조

```
config/
  AutoConfig                        조건부 자동 설정. @Bean 으로 명시
  NPlusOneSettingCheck              검사만 켜고 카운팅을 안 켠 구성을 경고. 검사를 켠 때만 로딩
  QueryCounterProperties            yml 프로퍼티. IDE 자동완성 메타데이터의 출처
  QueryCounterSettings              프로퍼티 이름과 기본값의 단일 출처. AutoConfig 와 리스너가 함께 읽는다
  DataSourceProxyBeanPostProcessor  DataSource 를 프록시로 감쌈
querycount/
  datasource/QueryCountListener     datasource-proxy 리스너. 여기서 기록이 시작된다
  context/QueryCountContext         ThreadLocal 수집소
  context/QueryInfo                 쿼리 하나의 값 객체. 테이블 이름을 정규식으로 추출
  assertion/QueryCounterAssertion   플루언트 빌더 (전체 대상)
  assertion/TableQueryAssertion     플루언트 빌더 (테이블별)
  assertion/ExpectedCount           기대값과 비교 방식(정확히 N, N 이하)을 담는 값 객체
  assertion/QueryCountVerifier      실제 비교와 오류 메시지 조립
  extension/QueryCountTestExecutionListener  Spring 테스트용. spring.factories 로 자동 등록
  extension/QueryCountTestExtension          JUnit 확장. Spring 컨텍스트를 띄우지 않는 테스트용
```

흐름은 세 층이다. **수집(Listener, Context) → 명세(Assertion) → 검증(Verifier).**

`smoke/` 는 루트 빌드에 포함되지 않는 별도 프로젝트다. 발행물을 Maven 저장소에서 받아
소비자로서 호출한다. 루트 빌드에 넣으면 프로젝트 소스를 그대로 쓰게 되어 검증 대상이
사라진다.

## 주의할 지점

### BeanPostProcessor에 프로퍼티 빈을 주입하지 않는다

`BeanPostProcessor`는 일반 빈보다 먼저 만들어진다. `QueryCounterProperties`를
주입받으면 프로퍼티 빈이 너무 이르게 초기화되고 Spring이 경고를 낸다.
`AutoConfig`에서 `Environment`로 값을 읽어 생성자에 넘긴다.

### 컨텍스트 로딩을 강제하지 않는다

`TestContext.getApplicationContext()`를 부르면 컨텍스트 로딩이 강제된다. 모든 Spring
테스트에서 도는 리스너에서는 받아들일 수 없다. ThreadLocal 정리는 활성 여부와 무관하게
무해하므로 조건 없이 수행한다.

**금지되는 것은 로딩을 강제하는 것이지 컨텍스트를 읽는 것 자체가 아니다.**
`QueryCountTestExecutionListener.recoverSettingsIfNoQueryRan` 이 유일한 예외다. 쿼리가 0건이라
설정이 기록 경로를 못 타고 온 경우에만, `hasApplicationContext()` 로 **이미 떠 있는 것을 확인한
뒤에** 읽는다. 안 떠 있으면 그냥 둔다. 컨텍스트가 없다는 것은 DataSource 도 없다는 뜻이라
보고할 것도 없다. 이 조건을 지키는지는 `ReportOnZeroQueryTest` 가 고정한다
(`getApplicationContext()` 를 부르지 않는지 검증하는 테스트가 있다).

활성 여부를 알려야 할 곳이 하나 있는데, 검증이 실패했고 기록된 쿼리가 0건일 때
`QueryCountVerifier`가 안내를 덧붙이는 자리다. 판단에 필요한 정보가 이미 ThreadLocal에
있으므로 별도 상태를 두지 않는다. 예전에 static 플래그를 썼다가 리셋되지 않아 테스트
불가능해져서 걷어냈다.

### 기록을 어느 스레드에 담을지는 한 자리에서 고른다

`QueryCountListener.route` 가 그 자리다. 테스트가 만들지 않은 스레드(톰캣 워커 등)에서 나간
쿼리는 `query-counter.other-threads.enabled` 가 켜져 있으면 `OtherThreadQueries` 에 모으고,
테스트가 끝날 때 `QueryCountContext.mergeOtherThreadQueries` 가 가져간다. **드레인은 판정보다
먼저 한다.** 합치기 전에 판정하면 합친 의미가 없다.

`OtherThreadQueries` 는 static 가변 상태다. 예전에 static 플래그가 리셋되지 않아 걷어낸 적이
있으므로 규율을 셋으로 정했다. 비어 있는 것이 안전한 기본이고, `clear()` 가 테스트 기록과 함께
비우고, 그 리셋을 `OtherThreadQueriesTest` 가 고정한다.

**꺼져 있을 때의 동작은 바꾸지 않는다.** 테스트 스레드가 아닌 곳의 기록을 아예 버리는 쪽으로
바꿔 봤더니 리스너를 직접 부르는 기존 테스트 다섯 개가 깨졌다. 그리고 `@TestExecutionListeners`
를 `MERGE_WITH_DEFAULTS` 없이 선언한 프로젝트에서는 우리 리스너가 안 돌아 컨텍스트가 비는데,
그런 구성에서 기록이 통째로 사라진다. 활성화하지 않은 사용자에게 영향을 주지 않는 것이 첫
성질이므로 그 방향은 버렸다.

대신 **상한을 뒀다.** 테스트 스레드가 아닌 곳의 기록이 `UNCOLLECTED_CAP`(1만) 에 닿으면 그
뒤는 담지 않고 한 번 알린다. 아무도 비우지 않는 기록이 무한히 쌓이는 것만 막고, 실제로 쓰이는
경로에는 닿지 않을 만큼 넉넉하게 잡았다. 안내에서 `other-threads.enabled` 를 가리켜 제대로 세는
길을 알려준다.

### 슬라이스 테스트는 자동 설정을 목록으로 골라 켠다

`@DataJpaTest` 는 자동 설정을 전부 켜지 않는다. 목록에 있는 것만 켜므로, 서드파티인 우리가
목록에 없으면 **DB 를 실제로 때리는 그 테스트에서 쿼리가 하나도 기록되지 않는다.** 실패하지
않고 조용히 안 도는 종류다.

그 목록에 들어가려고 `src/main/resources/META-INF/spring/` 에 등록 파일을 둔다. **슬라이스마다
버전별로 두 이름이 한 쌍이다.** 3.x 는 슬라이스 애노테이션이 `boot.test.autoconfigure` 아래
모여 있고, 4.x 는 모듈별로 흩어져 있다.

| 슬라이스 | 3.x | 4.x |
|---|---|---|
| `@DataJpaTest` | `...boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa` | `...boot.data.jpa.test.autoconfigure.AutoConfigureDataJpa` |
| `@JdbcTest` | `...boot.test.autoconfigure.jdbc.AutoConfigureJdbc` | `...boot.jdbc.test.autoconfigure.AutoConfigureJdbc` |
| `@DataJdbcTest` | `...boot.test.autoconfigure.data.jdbc.AutoConfigureDataJdbc` | `...boot.data.jdbc.test.autoconfigure.AutoConfigureDataJdbc` |

모르는 쪽은 무시되므로 둘 다 두는 것이 맞다. 목록에 있어도 `enabled=true` 조건은 그대로라
"켜지 않으면 아무 영향이 없다" 는 성질은 깨지지 않는다.

**이름을 짐작하지 않는다.** 틀리면 조용히 아무것도 안 하는 종류다. 해당 버전의
`spring-boot-test-autoconfigure`(3.x) 또는 모듈 jar(4.x)를 열어 `.imports` 목록에서 확인한다.
`@WebMvcTest` 는 DataSource 가 없어 대상이 아니고, `@JooqTest` 는 같은 기제로 될 것으로 보이나
쓰는 사람이 없어 넣지 않았다.

`SliceTestImportsTest` 가 파일의 존재와 내용을 고정한다. 실제 `@DataJpaTest` 안에서 기록되는지는
그 테스트가 보지 못한다 — 애노테이션 import 경로가 버전마다 달라 한 소스로 두 버전을 컴파일할
수 없다. 그쪽은 실제 프로젝트에 붙여 확인한다.

### 조용히 안 도는 구성은 경고로 알린다

`query-counter.n-plus-one.enabled=true` 만 켜면 아무 일도 일어나지 않는다. `AutoConfig`가
`query-counter.enabled=true`에만 걸려 있어 DataSource를 감싸지 않고, 감싸지 않으면 기록도
모드 전달도 없다. **실패하지 않고 틀리는 종류라** 사용자는 검사가 도는 줄 안다.

`NPlusOneSettingCheck`가 이 구성을 경고한다. 검사를 켠 구성에서만 로딩되므로 켜지 않은
프로젝트에는 클래스가 만들어지지도 않고, 실패시키지 않고 경고만 남긴다. 설정 실수로 남의
빌드를 세우지 않는다.

**설정을 더할 때 같은 것을 확인한다.** 켰는데 아무 일도 안 일어나는 조합이 생기면 그 조합을
알려 줄 자리를 함께 만든다.

### 설정은 기록 경로를 타고 테스트 경계로 간다

전역 N+1 검사가 이 제약을 정면으로 맞았다. 설정은 컨텍스트에 있는데 검사는 테스트 경계에서
해야 하고, 리스너는 컨텍스트 로딩을 강제할 수 없고 static 플래그는 위에 적힌 이유로 못 쓴다.

그래서 모드를 **기록하는 쪽이 실어 나른다.** `AutoConfig`가 프로퍼티를 읽어
`DataSourceProxyBeanPostProcessor`와 `QueryCountListener`에 넘기고, 그 리스너가 쿼리를 기록할
때 `QueryCountContext.requestNPlusOneCheck`로 ThreadLocal에 함께 넣는다. 테스트가 끝나면
`NPlusOneWatch`가 그 ThreadLocal을 보고 판단한다.

`clear()`가 쿼리와 모드를 함께 비우므로 테스트 사이에 남지 않는다. 컨텍스트가 여럿이라 모드가
섞이는 경우도 문제가 없다. 활성화되지 않은 컨텍스트는 DataSource를 감싸지 않아 기록이 0건이고,
기록이 없으면 검사할 것도 없다.

**보고는 다르다.** 0건이라는 사실 자체가 사용자가 물은 답이라, 기록이 없어도 보고는 나가야 한다.
그 한 경우만 리스너가 이미 떠 있는 컨텍스트에서 설정을 되살린다. 위의 "컨텍스트 로딩을 강제하지
않는다" 를 함께 읽는다.

**모드를 옮길 자리를 새로 만들 때 이 경로를 따른다.** 리스너에서 컨텍스트를 읽거나 static에
담는 쪽으로 돌아가면 위의 두 실패를 다시 겪는다.

### 알려진 빚

건드릴 일이 있으면 함께 정리한다. 지금 당장 급하지는 않다.

| 위치 | 내용 |
|---|---|
| `QueryCountListener` | `queryTypeCache` 가 SQL 문자열 키의 static 맵인데 비워지지 않는다 |
| `QueryCountListener` | `elapsedMs` 가 실행 단위 값인데 배치의 모든 쿼리에 같은 값이 붙는다 |
| `QueryInfo` | 생성자가 테이블 이름을 항상 정규식으로 추출한다. 안 쓰는 경우에도 |
| `QueryCountListener` | `queryTypeCache` 와 같은 성질로, 상한에 닿은 뒤에는 테스트 스레드 밖 기록을 버린다. 무한히 쌓이는 것은 막았지만 읽을 수도 없는 기록을 1만 개까지는 들고 있다 |
| `QueryCountVerifier` | 301줄에 private 메서드 24개. 검사를 하나 더 추가하기 전에 검사 단위를 인터페이스로 뽑는 편이 낫다 |
| `QueryCounterAssertion` | 검증하지 않은 어서션을 static ThreadLocal 목록으로 들고 있다. 리스너가 비우지만 전역 상태가 하나 늘어난 것은 사실이다 |

## 작업 규칙

**브랜치와 PR을 거친다. `master`에 직접 커밋하지 않는다.**

이슈는 항상 만들지 않는다. 아래 셋 중 하나라도 해당할 때만 만든다.

| 조건 | 왜 |
|---|---|
| 코드를 쓰기 전에 정해야 할 판단이 있다 | 결정과 그 근거가 diff 에 남지 않는다. 나중에 왜 그렇게 했는지 되짚을 곳이 필요하다 |
| 하나의 발견이 여러 PR로 갈린다 | PR들을 묶는 자리가 있어야 전체 그림이 보인다 |
| 남이 재현하거나 이어받을 정보가 diff 밖에 있다 | 재현 로그, 실측값, 외부 저장소 이슈 링크 같은 것들이다 |

해당하지 않으면 **PR 하나로 끝낸다.** 기계적인 수정, 문서 오타, 설정 추가가 여기 해당한다.
PR 본문에 배경과 판단을 적으면 이슈에 같은 내용을 한 번 더 쓸 이유가 없다.

판단이 서지 않으면 만들지 않는 쪽을 고른다. 필요해지면 그때 만들어 PR을 연결하면 된다.

| 항목 | 규칙 |
|---|---|
| base 브랜치 | `master` (단일 트렁크) |
| 유지보수 브랜치 | 지금은 없다. 이미 나간 버전을 패치해야 할 때 `0.0.x` 형태로 만든다 |
| 브랜치 이름 | 이슈가 있으면 `<type>/#<이슈번호>` 예: `feat/#42`. 없으면 `<type>/<짧은-설명>` 예: `chore/issue-policy` |
| 커밋과 PR 제목 | 이슈가 있으면 `[#이슈번호] Type: 한국어 설명`. 없으면 `Type: 한국어 설명` |
| Type | `Feat`, `Fix`, `Docs`, `Refactor`, `Chore`, `Setting`, `Test` |
| 머지 | 스쿼시. 제목에 ` (#PR번호)`가 붙는다 |
| 이슈 본문 | 이슈를 만들 때는 `.github/ISSUE_TEMPLATE/` 의 타입별 템플릿 |
| 라벨 | 타입 라벨(`Feat`, `Fix` 등)은 두지 않는다. 제목 접두사와 같은 말이라 정보가 없었다. GitHub 기본 라벨만 남겼고 템플릿이 자동으로 붙이지 않는다. 붙일지는 그때 판단한다 |
| PR 본문 | `.github/PULL_REQUEST_TEMPLATE.md` |

## 문서

| 파일 | 용도 |
|---|---|
| `README.md` | 영문. 기본 문서다. 사용자가 처음 보는 곳 |
| `README.ko.md` | 한국어. 두 문서 상단에서 서로 링크한다 |
| `CHANGELOG.md` | Keep a Changelog 형식. 릴리스 전 변경은 `[Unreleased]` 절에 쌓는다 |

**둘 중 하나만 고치지 않는다.** 사용법이나 API가 바뀌면 두 문서를 함께 고친다.

에러 메시지 형식을 README에 예시로 적어둔 곳이 있다. 메시지를 바꾸면 그 예시도 함께 고친다.
과거에 실행 시간 메시지가 코드와 어긋난 채 남아 있었다.

## 주석과 표기

### 주석 언어

읽는 사람이 누구인지로 갈린다. 공개 API 는 이 라이브러리를 쓰는 사람이 IDE 에서 보고,
내부 구현은 이 저장소를 고치는 사람만 본다.

| 대상 | 언어 |
|---|---|
| 공개 API 의 Javadoc | **영어.** 사용자가 IDE 자동완성과 Javadoc 에서 본다 |
| `QueryCounterProperties` 의 프로퍼티 설명 | **영어.** IDE 자동완성 메타데이터로 나간다 |
| 내부 클래스의 Javadoc | **한국어.** `AutoConfig`, `DataSourceProxyBeanPostProcessor`, `QueryCountVerifier` |
| 메서드 안의 구현 주석 (`//`) | **한국어.** 왜 이렇게 했는지를 적는 자리다 |
| 테스트 | **한국어.** `@DisplayName` 이 한국어라 영어 주석과 섞으면 읽기 나쁘다 |

공개 API 는 `QueryCounterAssertion`, `TableQueryAssertion`, `ExpectedCount`,
`QueryCountContext`, `QueryCountTestExtension`, `QueryCountTestExecutionListener`,
`QueryCounterProperties` 다.

### Javadoc 안의 코드 표기

**`{@code ...}` 로 감싼다. 백틱을 쓰지 않는다.** 백틱은 Javadoc 에서 코드로 렌더링되지 않고
문자 그대로 나온다. 마크다운을 쓰는 자리(README, 이 파일, PR 본문)와 갈리는 지점이라
옮겨 적을 때 놓치기 쉽다.

### 다른 저장소의 이슈 번호

**백틱으로 감싼다.** `` `quick-perf/quickperf#199` `` 처럼 쓴다.

감싸지 않으면 GitHub 이 그 저장소에 상호 참조를 남긴다. 이미 닫힌 이슈에도 알림이 가고
타임라인이 지저분해진다. 남의 저장소에 노이즈를 남기지 않는다. 상호 참조는 한 번 생기면
본문을 고쳐도 지워지지 않으므로, 애초에 만들지 않는 것이 유일한 대응이다.

적용 범위는 GitHub 이 렌더링하는 곳 전부다. **커밋 메시지도 포함된다.** 놓치기 쉬운 자리다.

| 자리 | 적용 |
|---|---|
| 이슈와 PR 의 본문, 댓글 | 적용 |
| 커밋 메시지 | 적용. GitHub 이 링크로 만든다 |
| Java 소스의 Javadoc 과 주석 | 해당 없음. 렌더링되지 않는다. 백틱 대신 그대로 쓴다 |

링크가 꼭 필요하면 URL 도 백틱 안에 넣는다. 이 저장소 안의 이슈와 PR 번호는 그대로 쓴다.
상호 참조가 목적에 맞는다.

## 테스트 규칙

| 항목 | 관례 |
|---|---|
| 메서드 이름 | 영어 camelCase. 예: `verifyShouldFailWhenCountsDoNotMatch` |
| `@DisplayName` | **한국어 문장.** 행위와 결과를 함께 쓴다 |
| 주석 | 한국어 |
| 단정 | AssertJ (`assertThat`, `assertThatThrownBy`) |
| 구조 | `// given` `// when` `// then` |
| 자동 설정 검증 | `ApplicationContextRunner` |
| 통합 테스트 | H2 + `@SpringBootTest(classes = AutoConfig.class, properties = "query-counter.enabled=true")` |

## 빌드

로컬 빌드는 **JDK 17**이다. `java.toolchain`이 17을 지정하고 CI 도 17로 돈다.

**JDK 25 로는 빌드가 안 뜬다.** `toolchain` 이 17을 지정해도 Gradle 8.13 자체가 JDK 25 에서
test 태스크를 못 만든다(`Could not create task of type 'Test'` / `Type T not present`).
쉘 기본이 25면 앞에 붙여 준다. JDK 21 과 17 은 둘 다 된다.

```sh
JAVA_HOME=~/.sdkman/candidates/java/21.0.12-tem ./gradlew test
```

```sh
./gradlew build
./gradlew test --tests '*AutoConfigTest*'
./gradlew spotlessApply

# 발행물을 소비자로서 받아 호출한다. 로컬에서 돌릴 때도 발행이 먼저다
./gradlew publishToMavenLocal -PpublishVersion=0.0.0-SMOKE-SNAPSHOT
./gradlew -p smoke test -PlibraryVersion=0.0.0-SMOKE-SNAPSHOT -PspringBootVersion=4.1.0
```

포맷 검사는 Spotless 다. `spotlessCheck` 가 `check` 에 물려 있어 `./gradlew build` 와
CI 가 함께 검사한다. 어긋나면 `./gradlew spotlessApply` 로 고친다.

**지금 스타일을 고정하는 설정이지 재포맷 도구가 아니다.** 규칙은 사용하지 않는 import 제거,
뒤 공백 제거, 파일 끝 개행, 들여쓰기 4칸 스페이스 네 가지뿐이다. spring-javaformat 은
들여쓰기가 탭이라 저장소 전체가 바뀌므로 쓰지 않는다. import 순서 규칙도 걸지 않았다.
checkstyle 은 없다.

## 릴리스

절차와 함정은 `docs/RELEASING.md` 에 있다. **릴리스할 때 그 파일을 먼저 연다.**

거기에 있는 것: 배포 좌표와 절차 다섯 단계, `build.gradle` 의 `version` 이 유일한 출처라는 것,
의존성 버전을 비워 두면 안 되는 이유(`0.2.0` 이 설치 불가로 나갔다), 서명 키와
`-PpublishVersion`, Publish 를 누른 뒤 Central 전파까지의 시차.

## 하지 않을 것

- **테스트 라이브러리에 네트워크 호출을 넣지 않는다.** 대시보드나 리포트가 필요하면
  `build/reports/` 에 자기완결 파일을 쓴다. JaCoCo와 Gradle 테스트 리포트가 그 방식이다
- 코드가 작다. 일반적인 추상화 작업을 미리 하지 않는다. 다음 기능이 이음새를
  알려줄 때 그 자리만 뽑는다
- 기능 추가와 리팩터링을 한 PR에 섞지 않는다
