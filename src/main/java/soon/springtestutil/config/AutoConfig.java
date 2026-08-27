package soon.springtestutil.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * query-counter 자동 설정입니다.
 *
 * <p>{@code query-counter.enabled=true} 인 경우에만 활성화됩니다. 활성화하지 않으면 DataSource를
 * 감싸지 않으므로 이 라이브러리를 쓰지 않는 테스트에는 아무 영향을 주지 않습니다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QueryCounterProperties.class)
@ConditionalOnProperty(prefix = "query-counter", name = "enabled", havingValue = "true")
public class AutoConfig {

    /**
     * DataSource를 감싸는 {@link org.springframework.beans.factory.config.BeanPostProcessor}를 등록합니다.
     *
     * <p>BeanPostProcessor는 일반 빈보다 먼저 만들어지므로 {@link QueryCounterProperties}를 주입받으면
     * 프로퍼티 빈이 너무 이르게 초기화됩니다. 그래서 {@link Environment}에서 값을 직접 읽습니다.
     * {@code QueryCounterProperties}는 IDE 자동완성용 메타데이터와 설정 문서화를 담당합니다.
     */
    @Bean
    static DataSourceProxyBeanPostProcessor dataSourceProxyBeanPostProcessor(Environment environment) {
        return new DataSourceProxyBeanPostProcessor(
            QueryCounterSettings.loggingEnabled(environment),
            QueryCounterSettings.nPlusOneCheck(environment),
            QueryCounterSettings.collectOtherThreads(environment),
            QueryCounterSettings.queryLimit(environment)
        );
    }

}
