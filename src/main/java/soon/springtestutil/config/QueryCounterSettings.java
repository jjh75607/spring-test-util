package soon.springtestutil.config;

import org.springframework.core.env.Environment;
import soon.springtestutil.querycount.NPlusOneCheck;
import soon.springtestutil.querycount.QueryLimit;

/**
 * 프로퍼티 이름과 기본값을 한 자리에 둡니다.
 *
 * <p>{@link AutoConfig} 와 테스트 경계 양쪽에서 같은 값을 읽어야 하는데, 두 곳에 같은 문자열을
 * 적어 두면 한쪽만 고쳐지는 날이 옵니다.
 */
public final class QueryCounterSettings {

    private QueryCounterSettings() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean countingEnabled(Environment environment) {
        return environment.getProperty("query-counter.enabled", Boolean.class, false);
    }

    public static boolean loggingEnabled(Environment environment) {
        return environment.getProperty("query-counter.logging.enabled", Boolean.class, false);
    }

    public static boolean collectOtherThreads(Environment environment) {
        return environment.getProperty("query-counter.other-threads.enabled", Boolean.class, false);
    }

    public static NPlusOneCheck nPlusOneCheck(Environment environment) {
        return NPlusOneCheck.of(
            environment.getProperty("query-counter.n-plus-one.enabled", Boolean.class, false),
            environment.getProperty("query-counter.n-plus-one.fail", Boolean.class, false)
        );
    }

    public static QueryLimit queryLimit(Environment environment) {
        return QueryLimit.of(
            environment.getProperty("query-counter.max-queries.per-test", Integer.class, 0),
            environment.getProperty("query-counter.max-queries.report", Boolean.class, false)
        );
    }

}
