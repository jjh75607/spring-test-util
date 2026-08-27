# query-counter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.jjh75607/query-counter)](https://central.sonatype.com/artifact/io.github.jjh75607/query-counter)
[![CI](https://github.com/jjh75607/query-counter/actions/workflows/ci.yml/badge.svg)](https://github.com/jjh75607/query-counter/actions/workflows/ci.yml)

[한국어](README.ko.md)

***
> **Keeps a fixed N+1 from coming back.**

You fixed the N+1 with a `join fetch`. Six months later someone adds a field to the DTO, changes a
repository method, or touches a lazy field from a new screen. **Nobody re-counts the queries in
that pull request.** The N+1 is back.

This library turns that into a failing test. It is a chain in the `then` block, with no annotation
on the test and no setup code inside it.

```java
QueryCounterAssertion.assertCounts()
    .forTables("member")
    .select(1)
    .noNPlusOne()
    .verify();
```

## What it prevents

- **A fixed N+1 coming back.** `noNPlusOne()` fails when the same SELECT runs with different
  parameter values. You do not need to know how much test data there is.
- **A query count creeping up unnoticed.** `select(3)` fails the build the moment it becomes 5,
  and lists the statements that ran.
- **A slow query slipping in.** `maxExecutionTimeMs(100)` fails when a single statement crosses it.
- **This library breaking other people's tests.** It leaves the DataSource untouched until you
  enable it. That is locked in by a test, not just documented.

## How it differs from other tools

| Library | How an assertion is written | What the test sets up |
|---|---|---|
| query-counter | A chain in the `then` block, taking `long` values that can be computed | One line of yml. The DataSource is wrapped for you |
| [QuickPerf](https://github.com/quick-perf/quickperf) | An annotation on the test method, such as `@ExpectSelect(1)` | Framework specific wiring, covered by its own guides |
| [datasource-assert](https://github.com/ttddyy/datasource-assert) | Assertions over the recorded executions of a `ProxyTestDataSource` | The proxy DataSource, constructed in the test |

Two differences show up in daily use. An expected count that follows the amount of test data or a
parameterized case is an expression here rather than a constant, and an annotation cannot hold an
expression. And nothing is recorded until `query-counter.enabled=true` is set, so adding the
dependency cannot change how existing tests behave.

QuickPerf reaches well beyond SQL, into JVM heap measurement among other things, so pick it up when
you want that range. The N+1 rule below follows how QuickPerf defines an N+1.

## Requirements

| | Version |
|---|---|
| Java | 17 or later |
| Spring Boot | 3.0 or later, including 4.x |

CI builds against Spring Boot 3.0.0, 3.5.x and 4.1.x on every change, so the range above is
verified rather than assumed. The library targets Java 17 bytecode, so it runs on any later JVM.

Every change also publishes the artifact and has a separate consumer project resolve it and call
it, on Spring Boot 3.0.0 and 4.1.0. **What is verified is the thing you download, not only the
source in this repository.**

## Install

Add the dependency to `build.gradle`. Nothing beyond `mavenCentral()` is needed.

```groovy
repositories {
    mavenCentral()
}

dependencies {
    testImplementation("io.github.jjh75607:query-counter:0.5.0")
}
```

Maven Central starts at `0.2.1`. `0.2.0` is on Central but its published dependency versions are
empty, so Gradle cannot resolve it, and anything earlier was never published there.

## Configure

Enable it in your test configuration, for example `src/test/resources/application.yml`.

```yaml
query-counter:
  enabled: true
```

Without this, the library stays inactive and no query is recorded. If an assertion fails and
nothing was recorded, the failure message reminds you of this setting.

## Usage

Write the assertion in the `then` block of a Spring test. **No annotation is required.**

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
            .forTables("member") // only assert queries against member
            .insert(1)           // expect one INSERT
            .verify();           // optional, see below
    }
}
```

**`verify()` is optional.** An assertion that is created but never verified is checked
automatically when the test method finishes, so a forgotten `verify()` can never make a test pass
silently. Call it when you want the failure to surface at that exact line.

If your test does not load a Spring test context, add
`@ExtendWith(QueryCountTestExtension.class)` so that recorded queries are still isolated between
tests. Spring tests do not need it.

### What gets counted

**Setup queries are counted too.** The recording is reset just before the test method starts, so
queries issued in `@BeforeEach` or in the `given` block are included. Call
`QueryCountContext.clear()` after your setup if you want to count only what the `when` block does.

**A JDBC batch counts as one.** Statements sent with `addBatch` and `executeBatch` are counted
once no matter how many were stacked, because that is one round trip to the database. If your
project enables `hibernate.jdbc.batch_size`, saving ten entities can produce a single INSERT
count rather than ten. Expect round trips, not rows.

## API

### Methods

| Method | Description |
|---|---|
| `forTable(String table)` | Assert on a single table. Chain the call to assert different expectations per table |
| `forTables(String... tables)` | Restrict the assertion to the given tables, counted together. When omitted, every table is counted |
| `forTables(List<String> tables)` | Same, taking a list |
| `select(long expected)` | Expected number of SELECT statements |
| `insert(long expected)` | Expected number of INSERT statements |
| `update(long expected)` | Expected number of UPDATE statements |
| `delete(long expected)` | Expected number of DELETE statements |
| `others(long expected)` | Expected number of any other statements |
| `maxExecutionTimeMs(long ms)` | Fail if a single query takes longer than this |
| `noNPlusOne()` | Fail if the same SELECT ran with different parameter values |
| `verify()` | Run the assertions now. Optional, since unverified assertions are checked after the test |

Expected values are `long`, so expressions work as well as constants. When the expected number of
queries depends on the amount of test data or on a parameterized case, compute it.

**Upper bounds.** Every count method also accepts an `ExpectedCount`, so you can assert a ceiling
instead of an exact number. A test that should keep passing after a small implementation change is
usually of this kind.

```java
import static soon.springtestutil.querycount.assertion.ExpectedCount.atMost;

QueryCounterAssertion.assertCounts()
    .select(atMost(3))                       // three or fewer passes
    .forTable("member").insert(atMost(1))    // works per table too
    .verify();
```

A plain number stays an exact match, so `select(3)` and `select(exactly(3))` are the same.

**Catching N+1.** `noNPlusOne()` fails when one SELECT shape was executed more than once with
different parameter values. That is the shape of an N+1: one query per parent row.

```java
QueryCounterAssertion.assertCounts()
    .noNPlusOne()
    .verify();
```

You do not have to know how much test data there is, which `select(1 + members.size())` requires.

Repeating a SELECT with the **same** parameter values is not reported. That is a duplicate read,
not an N+1. A statement sent as a JDBC batch is one round trip, so it is not reported either.

There is no threshold: a single repetition with differing values fails. Use `select(atMost(n))`
when you want to allow a number of queries instead. When `forTables` is set, only queries against
those tables are considered.

**What this rule also catches.** The rule is "same SELECT shape, different parameter values", and
some test styles produce that without an N+1 being present. Running the check across four public
test suites, 38 of the 83 reported query shapes were of that kind. Which ones you see depends far
more on how the suite is written than on the code under test: one suite reported 4 out of 46, and
another reported all of its 25.

| Test style | What the report looks like | Example |
|---|---|---|
| One test asserts a match and a miss | The finder runs twice with different arguments | `findByLastName("Davis")` then `findByLastName("Daviss")` |
| One test walks through pages | The same query runs with the next cursor or offset | reading page 1, then page 2 |
| One test drives a lifecycle | Create, update and delete each re-read the same row by id | `register(...)` called three times in a row |

None of these can be separated from a real N+1 by the number of repetitions. A genuine lazy-load
N+1 can run only twice when the test touches two parents, and a repeated finder call can run seven
times. Read the reported SQL and parameters before changing code: a real N+1 takes its parameters
from the rows of the query that ran just before it, while the cases above take them from the test.

`noNPlusOne()` applies to one test you chose, so this is mostly a concern for the suite-wide
`query-counter.n-plus-one.enabled`. It only warns until `query-counter.n-plus-one.fail` is set, so
turn `fail` on after you have looked through what the warnings say.

### Examples

Each example below is a real test in
[`src/test/java/soon/springtestutil/example/QueryCounterExampleTest.java`](src/test/java/soon/springtestutil/example/QueryCounterExampleTest.java),
so it compiles and runs with `./gradlew build`. Only the display names and the comment language
are adapted here. That package uses JPA entities and shows an N+1 appearing and then disappearing
after a `join fetch`.

`saveMembersInSeparateTeams(n)` saves n members, each in a team of its own, then flushes and clears
the persistence context. Every member needs a different team for an N+1 to be visible at all, since
a shared team would be read once and then served from the persistence context.

```java
@Test
void countByQueryType() {
    // given - one team and one member are saved, so two INSERTs
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

    // then - members are read at once, and a team is read once per member
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

    // then - a single statement over the limit raises an AssertionError.
    // On in-memory H2 every query stays well under it.
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

    // then - an annotation cannot express this
    QueryCounterAssertion.assertCounts()
        .select(1 + memberCount)
        .verify();
}
```

## Failures

- An `AssertionError` is thrown when the number of queries differs from what you expected.
- An `AssertionError` is thrown when a query takes longer than `maxExecutionTimeMs`.
- An `AssertionError` is thrown when `noNPlusOne()` finds a repeated SELECT with differing parameters.
- Every mismatch is reported together, so you do not fix them one at a time.

### Message format

```text
java.lang.AssertionError: [Test: {package}.{class}#{method}] Query count assertion failed:
QueryType.SELECT: expected 1, but was 4
  [1] select m1_0.id,m1_0.name,m1_0.team_id from member m1_0
  [2] select t1_0.id,t1_0.name from team t1_0 where t1_0.id=?
  [3] select t1_0.id,t1_0.name from team t1_0 where t1_0.id=?
  ... and 1 more
```

The statements that were counted are listed, so you do not have to turn on SQL logging and run the
test again to find out which ones they were. At most three are shown.

```text
java.lang.AssertionError: [Test: {package}.{class}#{method}] Table-specific query count assertion failed:
Table 'member' - QueryType.SELECT: expected 2, but was 1
  [1] select m1_0.id,m1_0.name from member m1_0
```

An upper bound reads the same way, with the comparison spelled out:

```text
java.lang.AssertionError: [Test: {package}.{class}#{method}] Query count assertion failed:
QueryType.SELECT: expected at most 2, but was 3
```

```text
java.lang.AssertionError: [Test: {package}.{class}#{method}]
Query execution time assertion failed: max=100ms, violations=1
[1] 120ms > 100ms, type=SELECT, SQL: SELECT * FROM member
```

An N+1 failure names the repeated statement and the values that differed, so you can see which
association to fetch:

```text
java.lang.AssertionError: [Test: {package}.{class}#{method}]
N+1 assertion failed: 1 query shape ran with different parameter values
[1] 3 executions, 3 distinct parameter values
    SQL: select t1_0.id,t1_0.name from team t1_0 where t1_0.id=?
    params: [1], [2], [3]
```

The check that runs for every test uses the same layout with a different first line, `N+1 detected`
while it only warns and `N+1 check failed` once `fail` is set:

```text
java.lang.AssertionError: [Test: {package}.{class}#{method}]
N+1 check failed: 1 query shape ran with different parameter values
[1] 3 executions, 3 distinct parameter values
    SQL: select t1_0.id,t1_0.name from team t1_0 where t1_0.id=?
    params: [1], [2], [3]
```

When an assertion fails and no query was recorded at all, the message adds a reminder, because the
usual cause is a missing setting:

```text
No query was recorded. Is query-counter.enabled=true set in your test configuration?
```

## Settings

| Property | Default | Description |
|---|---|---|
| `query-counter.enabled` | `false` | Count queries. When enabled, the DataSource is wrapped in a proxy that records every executed query |
| `query-counter.logging.enabled` | `false` | Log every executed SQL statement through SLF4J. Independent of counting, and off by default because always-on SQL logging is noisy in projects with many tests |
| `query-counter.n-plus-one.enabled` | `false` | Check every test for an N+1, with nothing written in the test |
| `query-counter.n-plus-one.fail` | `false` | Fail the test when that check finds one. When false it is logged as a warning |
| `query-counter.other-threads.enabled` | `false` | Count queries a test caused on another thread, such as a request handled by the server in an acceptance test |
| `query-counter.max-queries.per-test` | `0` | Fail a test that runs more queries than this. 0 means no limit |
| `query-counter.max-queries.report` | `false` | Log the query count of every test, to help pick the limit |

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

Every property appears with a description in IDE autocompletion.

### A ceiling on queries per test

An assertion written by hand says an exact number for one test. A ceiling says a rough number for
every test, with nothing written in any of them.

```yaml
query-counter:
  enabled: true
  max-queries:
    per-test: 50
```

**It catches a different thing than an exact assertion does.** A loop around a repository call turns
3 queries into 200, and the ceiling catches that in whichever test runs it. A count creeping from 3
to 12 stays under any useful ceiling, and that is fine: the ceiling is a net for runaway queries, not
a regression check.

Pick the number from what the suite runs today rather than from a round figure.

```yaml
query-counter:
  enabled: true
  max-queries:
    report: true
logging:
  level:
    soon.springtestutil: info
```

That logs one line per test, so the highest number in the suite is easy to find, and then a ceiling
above it will not fail anything that passes today. **The logging line matters**: a test setup with
`logging.level.root: warn` swallows the report. If that happens, a warning says so rather than
leaving the impression that nothing was counted.

Two things count toward the ceiling that are easy to forget. Queries a test setup runs, including a
listener that truncates tables between tests, are counted like any other. And with
`other-threads.enabled` on, queries the server ran for a request are counted too. Both are part of
what the test actually caused, so the number to pick is the one the report shows.

### Counting queries that ran on another thread

A test that sends a real HTTP request has its queries run by the server, on a worker thread. What
gets recorded is per thread, so from the test nothing is visible: an acceptance test sees zero
queries no matter how many the request ran.

`query-counter.other-threads.enabled` collects those and merges them into the test that caused
them, before any assertion or check runs. Counts, `noNPlusOne()`, and the global N+1 check all see
the same merged set.

**It is off by default because it assumes one test runs at a time in the JVM.** Sequential JUnit
holds that, and so does Gradle's `maxParallelForks`, which uses a separate JVM per fork. Parallel
execution inside one JVM does not: a request from one test can be attributed to another.

Two more things to know before turning it on.

- A query from `@Scheduled` or another background job lands on whichever test was running. Exact
  counts written by hand can start to move in a project that has those.
- A request that keeps working after the response is sent can have its last queries attributed to
  the next test.

Turning it on makes tests visible that were not before, so a project already running the global N+1
check may see the warning count jump. `fail: false` is the reason that default exists.

### Checking every test for an N+1

`noNPlusOne()` covers the test you write it in. That means a suite with hundreds of existing tests
gets no cover at all, which is where an N+1 you fixed six months ago comes back.

Turning on `query-counter.n-plus-one.enabled` runs the same check at the end of every test, with
nothing written in the test.

**`query-counter.enabled=true` has to be on as well.** Queries are only recorded when the DataSource
is wrapped, and that only happens when counting is enabled, so this check on its own does nothing at
all. Both go in the same block:

```yaml
query-counter:
  enabled: true
  n-plus-one:
    enabled: true
```

Forgetting the first one logs a warning at startup, since the check silently doing nothing is worse
than being told.

**It only warns until you also set `fail`.** Turning the check on in a suite that never had it will
surface every N+1 already there, all at once. Failing all of them on the first run leaves nothing to
do but switch the check back off, so the order is: turn it on, read the list, work through it, then
set `fail: true` to keep it from coming back.

The rule is the same one `noNPlusOne()` uses: the same SELECT running with different parameter
values. Repeats with identical values are duplicate reads, not an N+1, and are not reported.

Nothing is written in the test. The example below is a real test in
[`src/test/java/soon/springtestutil/example/GlobalNPlusOneExampleTest.java`](src/test/java/soon/springtestutil/example/GlobalNPlusOneExampleTest.java),
so it compiles and runs with `./gradlew build`.

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

        // when - members are read, then each team name is touched
        memberRepository.findAllLazily().forEach(member -> member.getTeam().getName());

        // then - there is nothing to write. The check runs once this method returns
    }

}
```

That test passes, and the warning it produces looks like the message below. With `fail: true` the
same finding fails the test instead.

### What the check does not see

It is a net, not a full sweep. Turning it on does not mean every N+1 in the suite is now covered.

| Not reported | Why |
|---|---|
| A test with a single row of fixture data | One repeat means one parameter value, and the rule needs two. The N+1 is in the code but nothing repeats |
| A query whose values are not bound | Values written into the SQL string make each execution a different statement, so they are never grouped |
| A repeated INSERT or UPDATE | Only SELECTs are considered |
| An association shared by every row | The persistence context reads it once and serves the rest from memory, so no repeat reaches the database |
| Queries executed on another thread | Not recorded unless `query-counter.other-threads.enabled` is on. A property of the counting itself, not of this check |

The other direction happens too. A test that reads in a loop on purpose, a parameterized test running
the same query with different values, and paging through results are all reported like any other
finding. That is what `fail: false` is for while you work through the list.

Where an exact number matters, an assertion written by hand still says it better than this check
does.

## Changelog

What changed in each release is in [CHANGELOG.md](CHANGELOG.md).
