package soon.springtestutil.querycount.extension;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import soon.springtestutil.config.QueryCounterSettings;
import soon.springtestutil.core.context.TestContextHolder;
import soon.springtestutil.querycount.QueryLimit;
import soon.springtestutil.querycount.context.QueryCountContext;
import soon.springtestutil.querycount.assertion.NPlusOneWatch;
import soon.springtestutil.querycount.assertion.QueryCounterAssertion;
import soon.springtestutil.querycount.assertion.QueryLimitWatch;
import soon.springtestutil.querycount.context.QueryCountContext;

/**
 * Wires query-counter into every Spring test without requiring an annotation.
 *
 * <p>Registered through {@code META-INF/spring.factories}, so the Spring TestContext
 * framework picks it up as soon as this library is on the test classpath. No
 * {@code @ExtendWith} is needed.
 *
 * <p>Responsibilities:
 *
 * <ul>
 * <li>Isolate tests by clearing the recorded queries before and after each test method.
 * <li>Record the test class and method name so that assertion failures identify the test.
 * <li>Verify assertions that were created but never verified, so a forgotten
 *     {@code verify()} cannot make a test pass silently.
 * </ul>
 *
 * <p>This listener never touches the application context. Resolving whether the library
 * is enabled through {@code testContext.getApplicationContext()} would force the context
 * to load, which is unacceptable for a listener that runs for every Spring test. Clearing
 * thread local state is harmless whether the library is enabled or not, so it is done
 * unconditionally.
 */
public class QueryCountTestExecutionListener implements TestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        TestContextHolder.setContext(
            testContext.getTestClass().getName(),
            testContext.getTestMethod().getName()
        );

        QueryCounterAssertion.clearPending();
        QueryCountContext.clear();
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        try {
            // 테스트가 다른 이유로 이미 실패했으면 자동 검증을 건너뛴다.
            // 그러지 않으면 진짜 실패 위에 쿼리 카운트 실패가 덮여 원인이 가려진다.
            if (testContext.getTestException() == null) {
                QueryCounterAssertion.verifyPending();
                NPlusOneWatch.run();
                recoverSettingsIfNoQueryRan(testContext);
                QueryLimitWatch.run();
            }
        }
        finally {
            QueryCounterAssertion.clearPending();
            TestContextHolder.clearContext();
            QueryCountContext.clear();
        }
    }

    /**
     * 쿼리가 한 건도 안 나간 테스트의 설정을 되살립니다.
     *
     * <p>설정은 기록 경로를 타고 오므로 쿼리가 없으면 전달되지 않습니다. N+1 검사는 기록이
     * 없으면 볼 것도 없어서 그래도 되지만, <b>보고는 다릅니다.</b> 0건이라는 사실 자체가
     * 사용자가 물은 답입니다. 여기서 되살리지 않으면 사용자는 쿼리가 0건인 것과 계측이 안
     * 붙은 것을 구별할 수 없습니다.
     *
     * <p>컨텍스트 로딩을 강제하지 않으려고 <b>이미 떠 있는 컨텍스트만</b> 읽습니다. 컨텍스트가
     * 없다는 것은 DataSource 도 없다는 뜻이라 보고할 것도 없습니다.
     */
    private void recoverSettingsIfNoQueryRan(TestContext testContext) {
        if (QueryCountContext.getQueryLimit().isActive() || !testContext.hasApplicationContext()) {
            return;
        }
        QueryLimit limit = QueryCounterSettings.queryLimit(
            testContext.getApplicationContext().getEnvironment());
        if (limit.isActive()) {
            QueryCountContext.requestQueryLimit(limit);
        }
    }

}
