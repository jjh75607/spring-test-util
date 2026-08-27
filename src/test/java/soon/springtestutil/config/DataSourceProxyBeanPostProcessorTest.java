package soon.springtestutil.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.support.AopUtils;

@ExtendWith(MockitoExtension.class)
class DataSourceProxyBeanPostProcessorTest {

    private DataSourceProxyBeanPostProcessor processor;

    @Mock
    private DataSource mockDataSource;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ProxyDataSource mockProxyDataSource;

    @Mock
    private Connection mockConnection;

    @BeforeEach
    void setUp() {
        processor = new DataSourceProxyBeanPostProcessor(false);
    }

    @DisplayName("DataSoruce가 아닌 빈은 프록시하지 않고 원본 빈을 반환한다.")
    @Test
    void nonDataSourceBeanShouldReturnOriginalBean() {
        // given
        Object nonDataSourceBean = new Object();
        String beanName = "ObjectBean";

        // when
        Object processedBean = processor.postProcessAfterInitialization(nonDataSourceBean,
            beanName);

        // then
        assertThat(processedBean).isSameAs(nonDataSourceBean);
    }

    @DisplayName("이미 ProxyDayaSource 타입의 빈은 프록시 하지 않고 원본 빈을 반환한다.")
    @Test
    void alreadyProxyDataSourceShouldReturnOriginalBean() {
        // given
        String beanName = "alreadyProxyDataSourceBean";

        // when
        Object processedBean = processor.postProcessAfterInitialization(mockProxyDataSource, beanName);

        // then
        assertThat(processedBean).isSameAs(mockProxyDataSource);
    }

    @DisplayName("일반 DataSource 빈은 프록시로 감싸서 반환한다.")
    @Test
    void dataSourceBeanShouldReturnProxyBean() {
        // given
        String beanName = "dataSourceBean";

        // when
        Object processedBean = processor.postProcessAfterInitialization(mockDataSource, beanName);

        // then
        assertThat(processedBean)
            .isNotSameAs(mockDataSource)
            .isInstanceOf(DataSource.class);

        // setProxyTargetClass(true)로 CGLIB 프록시를 사용하므로
        assertThat(AopUtils.isCglibProxy(processedBean)).isTrue();
        assertThat(AopUtils.isAopProxy(processedBean)).isTrue();
    }

    @DisplayName("프록시된 DataSource의 getConnection 호출 시 프록시의 getConnection이 호출된다.")
    @Test
    void getConnectionShouldDelegateToProxy() throws Exception {
        // given
        String beanName = "dataSourceBeanWithConnection";

        given(mockDataSource.getConnection())
            .willReturn(mockConnection);

        // when
        DataSource proxiedDataSource = (DataSource) processor.postProcessAfterInitialization(mockDataSource, beanName);

        // then
        assertThat(proxiedDataSource)
            .isNotNull()
            .isNotSameAs(mockDataSource);

        Connection connection = proxiedDataSource.getConnection();
        assertThat(connection)
            .isNotNull()
            .isNotSameAs(mockConnection);

        then(mockDataSource).should().getConnection();
    }

    @DisplayName("감싼 DataSource 가 던진 예외는 타입 그대로 나간다.")
    @Test
    void exceptionFromDataSourceKeepsItsType() throws SQLException {
        // given
        SQLException thrown = new SQLException("connection refused");
        given(mockDataSource.getConnection()).willThrow(thrown);
        DataSource proxied = (DataSource) processor.postProcessAfterInitialization(
            mockDataSource, "dataSource");

        // when, then
        // Method.invoke 가 감싼 InvocationTargetException 을 벗기지 않으면 호출자는
        // SQLException 을 못 받는다. 스프링과 하이버네이트의 예외 변환이 그때 통째로 건너뛰어진다.
        assertThatThrownBy(proxied::getConnection)
            .isSameAs(thrown);
    }

}
