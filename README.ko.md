# query-counter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.jjh75607/query-counter)](https://central.sonatype.com/artifact/io.github.jjh75607/query-counter)
[![CI](https://github.com/jjh75607/query-counter/actions/workflows/ci.yml/badge.svg)](https://github.com/jjh75607/query-counter/actions/workflows/ci.yml)

[English](README.md)

***
> **고쳐놓은 N+1 이 다시 돌아오는 것을 테스트가 막습니다.**

`join fetch` 로 N+1 을 고쳤습니다. 여섯 달 뒤 누가 DTO 에 필드를 더하고, 리포지토리 메서드를
바꾸고, 지연 로딩 필드를 새 화면에서 건드립니다. **그 PR 에서 아무도 쿼리 수를 다시 세지
않습니다.** 그리고 N+1 이 돌아옵니다.

이 라이브러리는 그걸 실패하는 테스트로 만듭니다. 테스트에 애노테이션을 붙이지 않고 셋업
코드도 넣지 않습니다. `then` 블록의 체인 하나입니다.

```java
QueryCounterAssertion.assertCounts()
    .forTables("member")
    .select(1)
    .noNPlusOne()
    .verify();
```

## 무엇을 막습니까

- **고친 N+1 이 돌아오는 것.** `noNPlusOne()` 은 같은 SELECT 가 서로 다른 파라미터 값으로
  반복되면 실패시킵니다. 테스트 데이터가 몇 건인지 몰라도 됩니다.
- **쿼리 수가 조용히 늘어나는 것.** `select(3)` 은 5가 되는 순간 빌드를 실패시키고, 실제로
  나간 SQL 을 함께 보여줍니다.
- **느린 쿼리가 슬쩍 들어오는 것.** `maxExecutionTimeMs(100)` 은 한 문장이라도 넘기면
  실패시킵니다.
- **이 라이브러리 때문에 남의 테스트가 깨지는 것.** 켜지 않으면 DataSource 를 건드리지
  않습니다. 문서로 약속한 게 아니라 테스트로 고정했습니다.

## 다른 도구와 무엇이 다릅니까

| 라이브러리 | 검증을 어떻게 쓰나 | 테스트가 준비할 것 |
|---|---|---|
| query-counter | `then` 블록의 체인. 값이 `long` 이라 계산해서 넘길 수 있습니다 | yml 한 줄. DataSource 는 알아서 감쌉니다 |
| [QuickPerf](https://github.com/quick-perf/quickperf) | 테스트 메서드에 붙이는 애노테이션. 예를 들어 `@ExpectSelect(1)` | 프레임워크별 연동. 자체 가이드가 있습니다 |
| [datasource-assert](https://github.com/ttddyy/datasource-assert) | `ProxyTestDataSource` 가 기록한 실행 목록에 대한 단정 | 테스트 안에서 프록시 DataSource 를 직접 구성 |

실제로 갈리는 지점은 둘입니다. 기대 쿼리 수가 테스트 데이터 개수나 파라미터화 케이스를 따라
달라지면 여기서는 상수가 아니라 식으로 씁니다. 애노테이션에는 식을 넣을 수 없습니다. 그리고
`query-counter.enabled=true` 를 넣기 전에는 아무것도 기록하지 않으므로, 의존성을 추가했다는
이유로 기존 테스트의 동작이 달라지지 않습니다.

QuickPerf 는 SQL 을 넘어 JVM 힙 측정까지 다룹니다. 그만한 범위가 필요하면 그쪽을 쓰시면
됩니다. 아래 N+1 판정 기준은 QuickPerf 가 N+1 을 정의하는 방식을 따랐습니다.

## 요구 사항

| | 버전 |
|---|---|
| Java | 17 이상 |
| Spring Boot | 3.0 이상. 4.x 포함 |

변경마다 CI 가 Spring Boot 3.0.0, 3.5.x, 4.1.x 로 빌드하므로 위 범위는 짐작이 아니라
검증된 값입니다. Java 17 바이트코드를 목표로 하므로 그 이후 JVM 에서도 돕니다.

그리고 변경마다 발행물을 실제로 내서, 별도의 소비자 프로젝트가 그것을 받아 호출하는 것까지
Spring Boot 3.0.0 과 4.1.0 에서 확인합니다. **검증되는 것이 이 저장소의 소스가 아니라 받아서
쓰는 그 물건입니다.**

## 설치

`build.gradle`에 의존성을 추가해주세요. `mavenCentral()` 외에 더 필요한 것은 없습니다.

```groovy
repositories {
    mavenCentral()
}

dependencies {
    testImplementation("io.github.jjh75607:query-counter:0.5.0")
}
```

Maven Central 은 `0.2.1` 부터 쓰실 수 있습니다. `0.2.0` 은 Central 에 올라가 있지만 발행된
의존성 버전이 비어 있어 Gradle 로 해석되지 않고, 그 이전 버전은 Central 에 올라간 적이 없습니다.

## 설정

테스트 설정에서 활성화합니다. 예를 들어 `src/test/resources/application.yml`입니다.

```yaml
query-counter:
  enabled: true
```

이 설정이 없으면 라이브러리가 동작하지 않고 쿼리도 기록되지 않습니다. 검증이 실패했는데 기록된
쿼리가 하나도 없으면 실패 메시지가 이 설정을 확인하라고 알려줍니다.

## 사용법

Spring 테스트의 `then` 블록에 검증을 씁니다. **애노테이션은 필요하지 않습니다.**

```java
@SpringBootTest
class MemberServiceTest {

    @Autowired
    private MemberService memberService;

    @Test
    void saveMember() {
        // given
        Member member = new Member("jjh75607", 12);

        // when
        memberService.createMember(member);

        // then
        QueryCounterAssertion.assertCounts()
            .forTables("member") // member 테이블에 대한 쿼리만 검증
            .insert(1)           // INSERT 1회 실행 예상
            .verify();           // 선택. 아래 설명 참고
    }
}
```

**`verify()`는 선택입니다.** 만들어두고 검증하지 않은 어서션은 테스트 메서드가 끝날 때 자동으로
검증되므로, `verify()`를 잊어서 테스트가 조용히 통과하는 일이 없습니다. 그 줄에서 바로 실패를
보고 싶을 때 호출하면 됩니다.

Spring 테스트 컨텍스트를 띄우지 않는 테스트라면 `@ExtendWith(QueryCountTestExtension.class)`를
붙여야 테스트 간에 기록이 격리됩니다. Spring 테스트에는 필요하지 않습니다.

### 무엇이 세어집니까

**셋업 쿼리도 카운트에 포함됩니다.** 기록은 테스트 메서드가 시작되기 직전에 초기화되므로
`@BeforeEach` 나 `given` 블록에서 실행한 쿼리가 함께 셉니다. `when` 블록의 쿼리만 세고 싶으면
셋업 뒤에 `QueryCountContext.clear()` 를 부르면 됩니다.

**JDBC 배치는 1건으로 셉니다.** `addBatch` 로 쌓고 `executeBatch` 로 보낸 문장은 몇 건을
쌓았든 1건입니다. 데이터베이스로 나가는 왕복이 한 번이기 때문입니다. `hibernate.jdbc.batch_size`
를 켠 프로젝트에서는 엔티티 10건을 저장해도 INSERT 카운트가 10이 아니라 1이 될 수 있습니다.
행 수가 아니라 왕복 횟수를 기대하시면 됩니다.

## API

### 메서드

| 메서드 | 설명 |
|---|---|
| `forTable(String table)` | 특정 테이블에 대한 검증 조건을 설정합니다. 체이닝으로 테이블마다 다른 조건 설정 가능 |
| `forTables(String... tables)` | 지정한 테이블들의 쿼리를 합쳐서 검증합니다. 지정하지 않으면 모든 테이블을 검증합니다 |
| `forTables(List<String> tables)` | 리스트로 지정 |
| `select(long expected)` | SELECT 쿼리 실행 횟수 |
| `insert(long expected)` | INSERT 쿼리 실행 횟수 |
| `update(long expected)` | UPDATE 쿼리 실행 횟수 |
| `delete(long expected)` | DELETE 쿼리 실행 횟수 |
| `others(long expected)` | 나머지 쿼리 실행 횟수 |
| `maxExecutionTimeMs(long ms)` | 개별 쿼리가 이 시간을 넘기면 실패 |
| `noNPlusOne()` | 같은 SELECT가 서로 다른 파라미터 값으로 실행되면 실패 |
| `verify()` | 지금 검증합니다. 선택 사항이며, 검증하지 않은 어서션은 테스트 후 자동으로 검사됩니다 |

기대값은 `long`이므로 상수뿐 아니라 식도 넘길 수 있습니다. 테스트 데이터의 개수나 파라미터화
테스트의 케이스에 따라 기대 쿼리 수가 달라지면 그대로 계산해서 지정하면 됩니다.

**상한 검증.** 카운트 메서드는 `ExpectedCount` 도 받습니다. 정확한 값이 아니라 상한을 검증할
수 있습니다. 구현 세부가 조금 바뀌어도 통과해야 하는 테스트가 여기 해당합니다.

```java
import static soon.springtestutil.querycount.assertion.ExpectedCount.atMost;

QueryCounterAssertion.assertCounts()
    .select(atMost(3))                       // 3개 이하면 통과
    .forTable("member").insert(atMost(1))    // 테이블별 검증에서도 됩니다
    .verify();
```

숫자를 그대로 넘기면 종전처럼 정확한 값 검증입니다. `select(3)` 과 `select(exactly(3))` 은
같습니다.

**N+1 잡기.** `noNPlusOne()` 은 하나의 SELECT가 서로 다른 파라미터 값으로 두 번 이상 실행되면
실패시킵니다. 부모 한 건마다 자식을 한 번씩 읽은 모양이 곧 N+1입니다.

```java
QueryCounterAssertion.assertCounts()
    .noNPlusOne()
    .verify();
```

`select(1 + members.size())` 와 달리 테스트 데이터가 몇 건인지 몰라도 됩니다.

파라미터 값까지 **같은** 반복은 실패시키지 않습니다. 그건 중복 조회이지 N+1이 아닙니다.
JDBC 배치로 나간 문장도 왕복이 한 번이라 대상이 아닙니다.

임계값은 없습니다. 값이 다른 반복이 하나라도 있으면 실패입니다. 몇 번까지 허용하고 싶으면
`select(atMost(n))` 을 쓰시면 됩니다. `forTables` 를 지정하면 그 테이블의 쿼리만 봅니다.

### 예제

아래 예제는 전부
[`src/test/java/soon/springtestutil/example/QueryCounterExampleTest.java`](src/test/java/soon/springtestutil/example/QueryCounterExampleTest.java)
에 있는 실제 테스트라 `./gradlew build` 로 컴파일과 실행이 검증됩니다. 여기서는 `@DisplayName`
만 덜어냈습니다. 그 패키지는 JPA 엔티티를 써서 N+1 이 생기는 모습과 `join fetch` 로 사라지는
모습을 보여줍니다.

`saveMembersInSeparateTeams(n)` 은 회원 n 명을 각자 다른 팀에 저장한 뒤 영속성 컨텍스트를
비웁니다. 회원마다 팀이 달라야 N+1 이 드러납니다. 팀을 공유하면 한 번만 읽고 그다음부터는
영속성 컨텍스트에서 꺼내기 때문입니다.

```java
@Test
void countByQueryType() {
    // given - 팀 1건과 회원 1건을 저장하므로 INSERT 2회
    saveMembersInSeparateTeams(1);

    // when
    memberRepository.findAllLazily();

    // then
    QueryCounterAssertion.assertCounts()
        .insert(2)
        .select(1)
        .verify();
}

@Test
void noNPlusOnePassesWithJoinFetch() {
    // given
    saveMembersInSeparateTeams(3);
    QueryCountContext.clear();

    // when
    memberRepository.findAllWithTeam().forEach(member -> member.getTeam().getName());

    // then
    QueryCounterAssertion.assertCounts()
        .noNPlusOne()
        .verify();
}

@Test
void differentExpectationsPerTable() {
    // given
    saveMembersInSeparateTeams(2);
    QueryCountContext.clear();

    // when
    memberRepository.findAllLazily().forEach(member -> member.getTeam().getName());

    // then - 회원은 한 번에 읽고, 팀은 회원마다 한 번씩 읽는다
    QueryCounterAssertion.assertCounts()
        .forTable("member").select(1)
        .forTable("team").select(2)
        .verify();
}

@Test
void assertExecutionTimeAlongWithCounts() {
    // given
    saveMembersInSeparateTeams(1);
    QueryCountContext.clear();

    // when
    memberRepository.findAllWithTeam();

    // then - 상한을 넘는 쿼리가 하나라도 있으면 AssertionError 가 발생한다.
    // 여기서는 H2 인메모리라 모든 쿼리가 상한 안에 들어온다.
    QueryCounterAssertion.assertCounts()
        .select(1)
        .maxExecutionTimeMs(1000)
        .verify();
}

@ParameterizedTest
@ValueSource(ints = {1, 3, 5})
void expectationPerParameterizedCase(int memberCount) {
    // given
    saveMembersInSeparateTeams(memberCount);
    QueryCountContext.clear();

    // when
    memberRepository.findAllLazily().forEach(member -> member.getTeam().getName());

    // then - 애노테이션으로는 표현할 수 없는 형태다
    QueryCounterAssertion.assertCounts()
        .select(1 + memberCount)
        .verify();
}
```

## 검증 실패

- 예상과 실제 쿼리 횟수가 다르면 `AssertionError`가 발생합니다.
- `maxExecutionTimeMs`를 초과하는 쿼리가 있으면 `AssertionError`가 발생합니다.
- `noNPlusOne()` 이 파라미터가 다른 SELECT 반복을 찾으면 `AssertionError`가 발생합니다.
- 어긋난 항목을 모두 모아 한 번에 보고하므로 하나씩 고쳐가며 발견하지 않아도 됩니다.

### 에러 메시지 형식

```text
java.lang.AssertionError: [Test: {패키지}.{클래스}#{메서드}] Query count assertion failed:
QueryType.SELECT: expected 1, but was 4
  [1] select m1_0.id,m1_0.name,m1_0.team_id from member m1_0
  [2] select t1_0.id,t1_0.name from team t1_0 where t1_0.id=?
  [3] select t1_0.id,t1_0.name from team t1_0 where t1_0.id=?
  ... and 1 more
```

센 쿼리가 함께 나오므로 어떤 쿼리였는지 보려고 SQL 로깅을 켜고 테스트를 다시 돌릴 필요가
없습니다. 최대 세 개까지 보여줍니다.

```text
java.lang.AssertionError: [Test: {패키지}.{클래스}#{메서드}] Table-specific query count assertion failed:
Table 'member' - QueryType.SELECT: expected 2, but was 1
  [1] select m1_0.id,m1_0.name from member m1_0
```

상한 검증도 같은 형식이고 비교 방식이 문구에 드러납니다.

```text
java.lang.AssertionError: [Test: {패키지}.{클래스}#{메서드}] Query count assertion failed:
QueryType.SELECT: expected at most 2, but was 3
```

```text
java.lang.AssertionError: [Test: {패키지}.{클래스}#{메서드}]
Query execution time assertion failed: max=100ms, violations=1
[1] 120ms > 100ms, type=SELECT, SQL: SELECT * FROM member
```

N+1 실패는 반복된 문장과 서로 달랐던 값을 함께 보여줍니다. 어떤 연관을 함께 읽어야 하는지
바로 보입니다.

```text
java.lang.AssertionError: [Test: {패키지}.{클래스}#{메서드}]
N+1 assertion failed: 1 query shape ran with different parameter values
[1] 3 executions, 3 distinct parameter values
    SQL: select t1_0.id,t1_0.name from team t1_0 where t1_0.id=?
    params: [1], [2], [3]
```

모든 테스트에서 도는 검사는 같은 형식에 첫 줄만 다릅니다. 경고만 남길 때는 `N+1 detected`,
`fail` 을 켜면 `N+1 check failed` 입니다.

```text
java.lang.AssertionError: [Test: {패키지}.{클래스}#{메서드}]
N+1 check failed: 1 query shape ran with different parameter values
[1] 3 executions, 3 distinct parameter values
    SQL: select t1_0.id,t1_0.name from team t1_0 where t1_0.id=?
    params: [1], [2], [3]
```

검증이 실패했는데 기록된 쿼리가 하나도 없으면 안내가 덧붙습니다. 대개 설정을 빠뜨린 경우입니다.

```text
No query was recorded. Is query-counter.enabled=true set in your test configuration?
```

## 설정 항목

| 프로퍼티 | 기본값 | 설명 |
|---|---|---|
| `query-counter.enabled` | `false` | 쿼리 카운팅. 켜면 DataSource를 프록시로 감싸 실행된 쿼리를 기록합니다 |
| `query-counter.logging.enabled` | `false` | 실행된 SQL을 SLF4J로 출력합니다. 카운팅과 별개이며, 항상 켜져 있으면 테스트가 많은 프로젝트에서 로그가 오염되므로 기본값은 꺼짐입니다 |
| `query-counter.n-plus-one.enabled` | `false` | 테스트에 아무것도 안 적고, 모든 테스트에서 N+1 을 검사합니다 |
| `query-counter.n-plus-one.fail` | `false` | 그 검사가 N+1 을 찾으면 실패시킵니다. 꺼져 있으면 경고 로그만 남깁니다 |
| `query-counter.other-threads.enabled` | `false` | 테스트가 다른 스레드에서 일으킨 쿼리도 셉니다. 인수 테스트에서 서버가 처리한 요청 같은 것입니다 |
| `query-counter.max-queries.per-test` | `0` | 한 테스트가 이 수를 넘으면 실패시킵니다. 0 이면 상한이 없습니다 |
| `query-counter.max-queries.report` | `false` | 테스트마다 쿼리 수를 로그로 남깁니다. 상한을 정할 때 씁니다 |

```yaml
query-counter:
  enabled: true
  logging:
    enabled: false
  n-plus-one:
    enabled: false
    fail: false
  other-threads:
    enabled: false
  max-queries:
    per-test: 0
    report: false
```

모든 프로퍼티가 IDE 자동완성에 설명과 함께 표시됩니다.

### 테스트별 쿼리 수 상한

손으로 적는 어서션은 한 테스트에 대해 정확한 수를 말합니다. 상한은 **모든 테스트에 대해 대략의
수**를 말하고, 어느 테스트에도 아무것도 안 적습니다.

```yaml
query-counter:
  enabled: true
  max-queries:
    per-test: 50
```

**정확한 어서션과 잡는 것이 다릅니다.** 반복문 안에서 리포지토리를 부르면 쿼리 3개가 200개가
되는데, 상한은 그것이 어느 테스트에서 일어나든 잡습니다. 3개가 12개로 조용히 늘어나는 것은 쓸만한
상한 아래에 머무릅니다. 그건 괜찮습니다. 상한은 폭주를 막는 그물이고 회귀 검사가 아닙니다.

숫자는 어림수로 잡지 말고 **지금 스위트가 실제로 쓰는 값**을 보고 정하세요.

```yaml
query-counter:
  enabled: true
  max-queries:
    report: true
logging:
  level:
    soon.springtestutil: info
```

이러면 테스트마다 한 줄씩 나오므로 스위트의 최대값을 찾기 쉽고, 그보다 여유 있게 상한을 잡으면
지금 통과하는 것이 깨지지 않습니다. **로깅 한 줄이 중요합니다.** 테스트 설정에서
`logging.level.root: warn` 을 두면 보고가 걸러집니다. 그 경우에는 경고가 그 사실을 알려 주므로,
켰는데 아무것도 안 세진 것처럼 보이는 일은 없습니다.

상한에 포함되는데 잊기 쉬운 것이 둘 있습니다. 테스트 준비 과정의 쿼리, 예컨대 테스트 사이에
테이블을 비우는 리스너의 쿼리도 다른 것과 똑같이 세집니다. 그리고 `other-threads.enabled` 를
켜면 서버가 요청을 처리하며 날린 쿼리도 세집니다. 둘 다 그 테스트가 실제로 일으킨 것이므로,
정할 숫자는 보고가 보여주는 그 값입니다.

### 다른 스레드에서 나간 쿼리도 세기

실제 HTTP 요청을 보내는 테스트는 쿼리가 서버 쪽 워커 스레드에서 나갑니다. 기록은 스레드마다
따로라서 테스트에서는 아무것도 보이지 않습니다. 요청이 쿼리를 몇 개 날렸든 인수 테스트에는
0으로 보입니다.

`query-counter.other-threads.enabled` 를 켜면 그것들을 모아서, 판정보다 먼저 그 테스트의 기록에
합칩니다. 카운트와 `noNPlusOne()` 과 전역 N+1 검사가 모두 합쳐진 같은 기록을 봅니다.

**기본이 꺼짐인 이유는 JVM 하나에서 테스트가 한 번에 하나만 돈다고 전제하기 때문입니다.**
순차로 도는 JUnit 은 그 전제가 맞고, Gradle 의 `maxParallelForks` 도 포크마다 JVM 이 따로라
괜찮습니다. JVM 안에서 병렬로 돌리면 한 테스트의 요청 쿼리가 다른 테스트에 붙습니다.

켜기 전에 알아둘 것이 둘 더 있습니다.

- `@Scheduled` 나 다른 배경 작업의 쿼리가 그때 돌던 테스트에 붙습니다. 그런 작업이 있는
  프로젝트에서는 손으로 적은 정확한 수가 흔들릴 수 있습니다.
- 응답을 보낸 뒤에도 요청 처리가 이어지면 마지막 쿼리 몇 개가 다음 테스트에 붙을 수 있습니다.

켜면 안 보이던 테스트가 보이기 시작하므로, 전역 N+1 검사를 이미 켜 둔 프로젝트에서는 경고 수가
크게 늘 수 있습니다. `fail: false` 가 기본인 이유가 그것입니다.

### 모든 테스트에서 N+1 을 검사하기

`noNPlusOne()` 은 그것을 적은 테스트만 덮습니다. 그러면 테스트가 이미 수백 개인 스위트는
아무것도 못 덮는데, 6개월 전에 고친 N+1 이 돌아오는 자리가 바로 거기입니다.

`query-counter.n-plus-one.enabled` 를 켜면 같은 검사가 모든 테스트 끝에서 돕니다. 테스트에는
아무것도 안 적습니다.

**`query-counter.enabled=true` 도 함께 켜야 합니다.** 쿼리는 DataSource 가 감싸질 때만 기록되고
그것은 카운팅을 켰을 때만 일어나므로, 이 검사만 켜면 아무 일도 하지 않습니다. 둘을 같은 블록에
씁니다.

```yaml
query-counter:
  enabled: true
  n-plus-one:
    enabled: true
```

앞엣것을 빠뜨리면 시작할 때 경고가 남습니다. 검사가 조용히 아무것도 안 하는 것보다 알려 주는
편이 낫습니다.

**`fail` 을 함께 켜기 전까지는 경고만 남깁니다.** 한 번도 검사한 적 없는 스위트에 켜면 이미
있던 N+1 이 한꺼번에 다 드러납니다. 첫 실행에서 그것들을 전부 실패시키면 검사를 다시 끄는 것
말고는 할 수 있는 일이 없으니, 순서는 이렇습니다. 켜서 목록을 읽고, 하나씩 고치고, 그다음
`fail: true` 로 돌아오지 못하게 막습니다.

판정 기준은 `noNPlusOne()` 과 같습니다. 같은 SELECT 가 파라미터 값을 바꿔 여러 번 실행된
경우입니다. 값까지 같은 반복은 N+1 이 아니라 중복 조회이므로 보고하지 않습니다.

**이 규칙이 함께 잡는 것.** 규칙이 "같은 SELECT 모양에 다른 파라미터 값"이라, N+1 이 아닌데도
그 모양이 되는 테스트 방식이 있습니다. 공개 테스트 스위트 넷에 돌렸더니 보고된 83개 중
38개가 그런 경우였습니다. 몇 개나 보일지는 검사 대상 코드보다 **스위트를 어떻게 썼는가**에
훨씬 크게 좌우됩니다. 어떤 스위트는 46개 중 4개였고 다른 스위트는 25개 전부였습니다.

| 테스트 방식 | 보고되는 모양 | 예 |
|---|---|---|
| 한 테스트에서 찾는 경우와 못 찾는 경우를 함께 확인 | 같은 조회가 인자만 바꿔 두 번 | `findByLastName("Davis")` 뒤에 `findByLastName("Daviss")` |
| 한 테스트에서 다음 장으로 넘기며 확인 | 같은 쿼리가 커서나 오프셋만 바꿔 | 1장을 읽고 2장을 읽기 |
| 한 테스트에서 생성, 수정, 삭제를 차례로 | 단계마다 같은 행을 id 로 다시 읽음 | `register(...)` 를 연달아 세 번 |

**반복 횟수로는 진짜와 못 가릅니다.** 테스트가 부모를 둘만 건드리면 진짜 지연 로딩 N+1 도
2회로 나오고, 조회를 반복해 부르는 테스트는 7회까지 갑니다. 코드를 고치기 전에 보고된 SQL 과
파라미터를 읽으십시오. 진짜 N+1 은 **바로 앞 쿼리가 돌려준 행**에서 파라미터를 받고, 위의
경우들은 테스트 코드에서 받습니다.

`noNPlusOne()` 은 직접 고른 테스트에만 적용되므로, 이 이야기는 주로 스위트 전체에 켜는
`query-counter.n-plus-one.enabled` 에 해당합니다.

테스트에는 아무것도 적지 않습니다. 아래 예제는
[`src/test/java/soon/springtestutil/example/GlobalNPlusOneExampleTest.java`](src/test/java/soon/springtestutil/example/GlobalNPlusOneExampleTest.java)
의 실제 테스트라 `./gradlew build` 로 컴파일되고 실행됩니다.

```java
@SpringBootTest(classes = ExampleApplication.class, properties = {
    "query-counter.enabled=true",
    "query-counter.n-plus-one.enabled=true"
})
@Transactional
class GlobalNPlusOneExampleTest {

    @Test
    void reportsNPlusOneWithoutAnyAssertion() {
        // given
        saveMembersInSeparateTeams(3);
        QueryCountContext.clear();

        // when - 회원을 읽고 각자의 팀 이름을 꺼낸다
        memberRepository.findAllLazily().forEach(member -> member.getTeam().getName());

        // then - 적을 것이 없다. 검사는 이 메서드가 끝난 뒤에 돈다
    }

}
```

이 테스트는 통과하고, 남는 경고는 아래 메시지 형식과 같습니다. `fail: true` 면 같은 것이
테스트를 실패시킵니다.

### 이 검사가 못 보는 것

전수 검사가 아니라 그물입니다. 켠다고 스위트의 N+1 이 전부 덮이는 것은 아닙니다.

| 보고하지 않는 경우 | 왜 |
|---|---|
| 픽스처 데이터가 1건인 테스트 | 반복이 1회면 파라미터 값도 하나인데 판정에는 둘이 필요합니다. 코드에 N+1 이 있어도 반복될 것이 없습니다 |
| 값을 바인딩하지 않는 쿼리 | 값이 SQL 문자열에 들어가면 실행마다 다른 문장이 되어 한 모양으로 묶이지 않습니다 |
| 반복되는 INSERT 나 UPDATE | SELECT 만 봅니다 |
| 모든 행이 같은 연관을 가리키는 경우 | 영속성 컨텍스트가 한 번 읽고 나머지는 메모리에서 주므로 DB 까지 반복이 가지 않습니다 |
| 다른 스레드에서 실행된 쿼리 | `query-counter.other-threads.enabled` 를 켜지 않으면 기록되지 않습니다. 이 검사가 아니라 카운팅 자체의 성질입니다 |

반대 방향도 있습니다. 판정 기준이 "같은 SELECT, 다른 파라미터 값" 이라, 코드에 N+1 이 없는데도
정확히 그 모양을 만드는 테스트가 세 종류 있습니다. 다른 것과 똑같이 보고되며, 목록을 정리하는
동안 `fail: false` 로 두는 이유가 그것입니다.

| 보고되지만 N+1 이 아닌 것 | 테스트가 하는 일 | 보고가 어떻게 보이나 |
|---|---|---|
| 긍정과 부정 두 경우를 한 테스트에서 확인 | `findByLastName("Davis")` 뒤에 `findByLastName("Daviss")` 를 불러 두 건과 0건을 각각 단언 | 같은 SELECT 2회, 파라미터는 테스트에 적힌 리터럴만 다르다 |
| 페이징이나 커서를 다음 장으로 넘김 | 첫 장을 읽고 거기서 받은 커서로 다음 장을 읽는다 | 같은 SELECT, 오프셋이나 커서 값만 다르다 |
| 같은 연산을 순차로 여러 번 호출 (라이프사이클) | 같은 레코드를 생성, 수정, 삭제하며 단계마다 id 로 다시 읽는다 | 같은 id 조회 SELECT 가 단계마다 한 번, 사이에 쓰기가 낀다 |

얼마나 자주 나는지는 프로젝트가 아니라 스위트가 어떻게 쓰였는지에 달렸습니다. 이 검사를 켜고
공개 스위트 넷에서 재 보니, 컬렉션을 읽고 연관을 만지는 저장소·서비스 테스트 스위트는 46건 중
4건이 이런 경우였고(나머지는 진짜 N+1), 서비스 시나리오 테스트 스위트는 9건 중 8건, 긍정·부정
두 경우와 페이징을 확인하는 저장소 테스트 스위트는 25건 중 25건이었습니다. 실행 횟수로도, 반복이
테스트의 첫 쿼리인지로도 진짜와 갈리지 않아 이 검사는 가르려 하지 않습니다. 보고를 테스트와
대조해 읽으십시오. N+1 은 앞선 쿼리가 돌려준 행마다 한 번씩 반복되고, 파라미터 값이 그 행들의
id 입니다.

정확한 숫자가 중요한 자리에는 이 검사보다 손으로 적은 어서션이 여전히 낫습니다.

## 변경 이력

릴리스마다 무엇이 바뀌었는지는 [CHANGELOG.md](CHANGELOG.md) 에 있습니다.
