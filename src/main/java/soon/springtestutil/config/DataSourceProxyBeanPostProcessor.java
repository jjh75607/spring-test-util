package soon.springtestutil.config;

import jakarta.annotation.Nonnull;
import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.ReflectionUtils;
import soon.springtestutil.querycount.NPlusOneCheck;
import soon.springtestutil.querycount.QueryLimit;
import soon.springtestutil.querycount.datasource.QueryCountListener;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * DataSource 빈을 프록시로 감싸 실행된 쿼리를 기록하는 BeanPostProcessor입니다.
 *
 * <p>{@link AutoConfig}가 {@code query-counter.enabled=true}인 경우에만 이 빈을 등록합니다.
 * 따라서 활성화하지 않은 프로젝트에서는 DataSource가 감싸지지 않습니다.
 *
 * <p>SQL 로깅은 {@code query-counter.logging.enabled}로 따로 켭니다. 쿼리 카운팅과 무관한
 * 기능이고 테스트가 많은 프로젝트에서는 로그가 오염되므로 기본값은 비활성입니다.
 *
 * <p>{@link PriorityOrdered}를 구현합니다. Spring은 {@code PriorityOrdered} BeanPostProcessor를
 * 가장 먼저 만들므로, {@code Ordered}만 구현하면서 DataSource에 의존하는 다른
 * BeanPostProcessor가 있어도 그보다 먼저 등록됩니다. 이것이 없으면 그런 BeanPostProcessor를
 * 만드느라 DataSource가 미리 만들어져 감싸지지 않고, 기록된 쿼리가 0건이 됩니다.
 **/
public class DataSourceProxyBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {

    private final boolean loggingEnabled;

    private final NPlusOneCheck nPlusOneCheck;

    private final boolean collectOtherThreads;

    private final QueryLimit queryLimit;

    public DataSourceProxyBeanPostProcessor(boolean loggingEnabled) {
        this(loggingEnabled, NPlusOneCheck.OFF, false, QueryLimit.OFF);
    }

    public DataSourceProxyBeanPostProcessor(boolean loggingEnabled, NPlusOneCheck nPlusOneCheck) {
        this(loggingEnabled, nPlusOneCheck, false, QueryLimit.OFF);
    }

    public DataSourceProxyBeanPostProcessor(
        boolean loggingEnabled,
        NPlusOneCheck nPlusOneCheck,
        boolean collectOtherThreads
    ) {
        this(loggingEnabled, nPlusOneCheck, collectOtherThreads, QueryLimit.OFF);
    }

    public DataSourceProxyBeanPostProcessor(
        boolean loggingEnabled,
        NPlusOneCheck nPlusOneCheck,
        boolean collectOtherThreads,
        QueryLimit queryLimit
    ) {
        this.loggingEnabled = loggingEnabled;
        this.nPlusOneCheck = nPlusOneCheck;
        this.collectOtherThreads = collectOtherThreads;
        this.queryLimit = queryLimit;
    }

    /**
     * {@code PriorityOrdered} 안에서는 가장 늦게 둡니다. DataSource를 감싸는 일은 다른
     * BeanPostProcessor가 DataSource에 손댈 기회를 가진 뒤에 하는 편이 안전합니다.
     */
    @Override
    public int getOrder() {
        return PriorityOrdered.LOWEST_PRECEDENCE - 1;
    }

    /**
     * 위임 사슬을 따라갈 최대 깊이입니다. DataSource가 서로를 가리키는 구성이 있어도
     * 무한히 돌지 않게 합니다.
     */
    private static final int MAX_DELEGATION_DEPTH = 16;

    @Override
    public Object postProcessAfterInitialization(
        @Nonnull Object bean,
        @Nonnull String beanName
    ) throws BeansException {
        if (bean instanceof DataSource dataSource && !alreadyCounted(dataSource)) {
            ProxyFactory factory = new ProxyFactory(bean);
            factory.setProxyTargetClass(true); // 다양한 DataSource 구현체를 지원하기 위해 CGLIB 프록시 사용
            factory.addInterface(QueryCountedDataSource.class);
            factory.addAdvice(ProxyDataSourceInterceptor.of(
                dataSource, this.loggingEnabled, this.nPlusOneCheck, this.collectOtherThreads,
                this.queryLimit));
            return factory.getProxy();
        }

        return bean;
    }

    /**
     * 이 DataSource를 거치는 쿼리가 이미 기록되는지 판단합니다.
     *
     * <p>자기 자신이 감싸져 있는 경우와, 위임하는 대상이 이미 감싸져 있는 경우를 모두 봅니다.
     * 뒤쪽이 중요합니다. {@code LazyConnectionDataSourceProxy}처럼 다른 DataSource 빈을
     * 위임하는 빈은 그 자신이 감싸진 적은 없지만, 위임 대상이 이미 감싸져 있으면 여기서 한 번
     * 더 감쌀 때 같은 쿼리가 두 번 기록됩니다.
     */
    private boolean alreadyCounted(DataSource dataSource) {
        DataSource current = dataSource;

        for (int depth = 0; current != null && depth < MAX_DELEGATION_DEPTH; depth++) {
            if (current instanceof QueryCountedDataSource || current instanceof ProxyDataSource) {
                return true;
            }
            current = targetOf(current);
        }

        return false;
    }

    /**
     * 위임 대상 DataSource를 꺼냅니다. 위임하지 않으면 {@code null}입니다.
     *
     * <p>{@code DelegatingDataSource}로 타입을 보지 않고 {@code getTargetDataSource} 메서드를
     * 찾는 이유는 그 클래스가 spring-jdbc에 있기 때문입니다. 이 라이브러리는 spring-jdbc에
     * 의존하지 않습니다. 여기서 의존을 만들면 DataSource 빈만 있고 spring-jdbc는 없는
     * 프로젝트에서 깨집니다.
     *
     * <p>덤으로 같은 이름의 접근자를 가진 직접 만든 위임 구현도 함께 알아봅니다. 그 밖의
     * 방식으로 위임하는 구현은 알아보지 못합니다.
     */
    private DataSource targetOf(DataSource dataSource) {
        Method method = ReflectionUtils.findMethod(dataSource.getClass(), "getTargetDataSource");
        if (method == null || !DataSource.class.isAssignableFrom(method.getReturnType())) {
            return null;
        }

        ReflectionUtils.makeAccessible(method);
        Object target = ReflectionUtils.invokeMethod(method, dataSource);
        return target instanceof DataSource targetDataSource ? targetDataSource : null;
    }

    /**
     * 실제 DataSource를 ProxyDataSource로 감싸고, 메서드 호출을 위임합니다.
     */
    private record ProxyDataSourceInterceptor(DataSource dataSource) implements MethodInterceptor {

        private static ProxyDataSourceInterceptor of(
            DataSource dataSource,
            boolean loggingEnabled,
            NPlusOneCheck nPlusOneCheck,
            boolean collectOtherThreads,
            QueryLimit queryLimit
        ) {
            ChainListener listener = new ChainListener();
            if (loggingEnabled) {
                listener.addListener(new SLF4JQueryLoggingListener());
            }
            listener.addListener(new QueryCountListener(nPlusOneCheck, collectOtherThreads, queryLimit));

            return new ProxyDataSourceInterceptor(
                ProxyDataSourceBuilder.create(dataSource)
                    .name("DataSource-Proxy")
                    .listener(listener)
                    .build()
            );
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            // 실제 DataSource의 메소드를 프록시 DataSource에서 호출
            Method proxyMethod = ReflectionUtils.findMethod(
                dataSource.getClass(),
                invocation.getMethod().getName(),
                invocation.getMethod().getParameterTypes()
            );
            if (proxyMethod != null) {
                // 프록시 객체에 메서드가 존재하면 해당 메서드를 호출
                try {
                    return proxyMethod.invoke(dataSource, invocation.getArguments());
                } catch (InvocationTargetException e) {
                    // Method.invoke 는 대상이 던진 예외를 감싼다. 벗기지 않으면 호출자가
                    // SQLException 대신 UndeclaredThrowableException 을 받고,
                    // 스프링과 하이버네이트의 예외 변환이 통째로 건너뛰어진다.
                    throw e.getCause() != null ? e.getCause() : e;
                }
            }
            return invocation.proceed();
        }

    }

}
