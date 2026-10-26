package com.neueda.leap.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;

import com.neueda.leap.trading.api.AccountController;
import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.repository.jpa.UserRepository;
import com.neueda.leap.trading.service.impl.TradingAccountService;

class OlapDataSourceConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            TransactionAutoConfiguration.class
        ))
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues(
            "spring.datasource.url=jdbc:h2:mem:account-oltp;MODE=PostgreSQL",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "olap.source.url=jdbc:h2:mem:account-oltp;MODE=PostgreSQL",
            "olap.source.username=sa",
            "olap.source.password=",
            "olap.datasource.url=jdbc:h2:mem:account-olap;MODE=PostgreSQL",
            "olap.datasource.username=sa",
            "olap.datasource.password="
        );

    @Test
    void keepsAccountOperationsOnJpaAndEtlOnItsOwnDatasource() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("transactionManager")).isInstanceOf(JpaTransactionManager.class);
            assertThat(context.getBean(PlatformTransactionManager.class))
                .isSameAs(context.getBean("transactionManager"));
            DataSource primary = context.getBean("dataSource", DataSource.class);
            DataSource olap = context.getBean("olapDataSource", DataSource.class);
            assertThat(context.getBean("transactionManager", JpaTransactionManager.class).getDataSource())
                .isSameAs(primary);
            assertThat(context.getBean("olapTransactionManager", DataSourceTransactionManager.class)
                .getDataSource()).isSameAs(olap);
            assertThat(context.getBean("olapJdbcTemplate", JdbcTemplate.class).getDataSource())
                .isSameAs(olap).isNotSameAs(primary);
            assertThat(context.getBean("oltpJdbcTemplate", JdbcTemplate.class).getDataSource())
                .isSameAs(context.getBean("oltpDataSource"));

            User user = new User();
            user.setUserId(1);
            user.setUsername("account-regression-test");
            user.setFirstName("Test");
            user.setLastName("User");
            user.setSalt("test-salt");
            user.setUserHashedSaltedPassword("test-hash");
            context.getBean(UserRepository.class).saveAndFlush(user);

            var mvc = MockMvcBuilders.standaloneSetup(context.getBean(AccountController.class)).build();
            String response = mvc.perform(post("/accounts")
                    .contentType("application/json")
                    .content("{\"userId\":1,\"accountType\":\"TRADING\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRADING"))
                .andReturn().getResponse().getContentAsString();
            int accountId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("accountId").intValue();

            context.getBean(TradingAccountService.class).deposit(accountId, new BigDecimal("25.00"));
            mvc.perform(get("/accounts/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(accountId))
                .andExpect(jsonPath("$[0].availableFunds").value(25.00));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan("com.neueda.leap.trading.domain")
    @EnableJpaRepositories("com.neueda.leap.trading.repository.jpa")
    @Import({OlapDataSourceConfiguration.class, AccountController.class, TradingAccountService.class})
    static class TestConfiguration {
    }
}
