package soon.springtestutil.querycount.extension;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.TestContext;
import soon.springtestutil.core.context.TestContextHolder;
import soon.springtestutil.querycount.QueryLimit;
import org.slf4j.LoggerFactory;
import soon.springtestutil.querycount.assertion.QueryCounterAssertion;
import soon.springtestutil.querycount.assertion.QueryLimitWatch;
import soon.springtestutil.querycount.context.QueryCountContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿼리를 한 건도 실행하지 않은 테스트에서도 보고가 살아 있는지 봅니다.
 *
 * <p>설정은 기록 경로를 타고 테스트 경계로 오므로, 쿼리가 없으면 상한이 전달되지 않아
 * 보고가 통째로 사라졌습니다. 그러면 사용자는 <b>쿼리가 0건인 것</b>과 <b>계측이 안 붙은 것</b>을
 * 구별할 수 없습니다. 계측이 안 붙는 결함을 두 번 고쳤던 라이브러리라 이 구별이 중요합니다.
 *
 * <p>되살리기는 테스트 본문이 아니라 그 뒤에 도는 콜백에서 일어나므로 콜백을 직접 부릅니다.
 */
@DisplayName("쿼리 0건일 때의 보고")
class ReportOnZeroQueryTest {

    private final QueryCountTestExecutionListener listener = new QueryCountTestExecutionListener();

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        reset();
        logger = (Logger) LoggerFactory.getLogger(QueryLimitWatch.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        reset();
    }

    private void reset() {
        QueryCounterAssertion.clearPending();
        QueryCountContext.clear();
        TestContextHolder.clearContext();
    }

    @DisplayName("쿼리가 한 건도 안 나가도 이미 떠 있는 컨텍스트에서 보고 설정을 되살린다")
    @Test
    void reportSettingIsRecoveredWhenNoQueryRan() {
        // given
        // 쿼리를 실행하지 않았으므로 기록 경로가 설정을 실어 오지 않았다.
        assertThat(QueryCountContext.getQueryLimit()).isEqualTo(QueryLimit.OFF);

        // when
        listener.afterTestMethod(testContextWith("query-counter.max-queries.report", "true"));

        // then
        // 되살린 값은 콜백 끝의 clear() 가 비우므로, 남는 증거는 보고가 나갔다는 사실이다.
        assertThat(appender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .anySatisfy(message -> assertThat(message).contains("0 queries"));
    }

    @DisplayName("컨텍스트가 아직 안 떴으면 로딩을 강제하지 않고 그냥 둔다")
    @Test
    void doesNotForceContextLoading() {
        // given
        TestContext testContext = Mockito.mock(TestContext.class);
        Mockito.when(testContext.hasApplicationContext()).thenReturn(false);

        // when
        listener.afterTestMethod(testContext);

        // then
        assertThat(appender.list).isEmpty();
        // 컨텍스트를 건드리면 로딩이 강제된다. 부르지 않았는지 확인한다.
        Mockito.verify(testContext, Mockito.never()).getApplicationContext();
    }

    private TestContext testContextWith(String key, String value) {
        MockEnvironment environment = new MockEnvironment().withProperty(key, value);
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        Mockito.when(applicationContext.getEnvironment()).thenReturn(environment);

        TestContext testContext = Mockito.mock(TestContext.class);
        Mockito.when(testContext.hasApplicationContext()).thenReturn(true);
        Mockito.when(testContext.getApplicationContext()).thenReturn(applicationContext);
        return testContext;
    }

}
