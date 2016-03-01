/*
 *
 *  Copyright 2015 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.genie;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import javax.validation.Validator;

/**
 * Spring configuration class for integration tests.
 *
 * @author tgianos
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan
public class GenieCoreTestApplication {

    /**
     * Setup bean validation.
     *
     * @return The bean validator
     */
    @Bean
    @ConditionalOnMissingBean
    public Validator localValidatorFactoryBean() {
        return new LocalValidatorFactoryBean();
    }

    /**
     * Setup method parameter bean validation.
     *
     * @return The method validation processor
     */
    @Bean
    @ConditionalOnMissingBean
    public MethodValidationPostProcessor methodValidationPostProcessor() {
        return new MethodValidationPostProcessor();
    }

    /**
     * The hostname bean to use for integration tests.
     *
     * @return localhost always
     */
    @Bean
    public String hostname() {
        return "localhost";
    }

//    /**
//     * Get the DBUnit configuration.
//     *
//     * @return The config bean
//     */
//    @Bean
//    public DatabaseConfigBean dbUnitDatabaseConfig() {
//        final DatabaseConfigBean dbConfig = new DatabaseConfigBean();
//        dbConfig.setDatatypeFactory(new HsqldbDataTypeFactory());
//        return dbConfig;
//    }
//
//    /**
//     * Get the database connection factory bean.
//     *
//     * @param dataSource The data source to use
//     * @return The database connection factory bean for dbunit.
//     */
//    @Bean
//    public DatabaseDataSourceConnectionFactoryBean dbUnitDatabaseConnection(final DataSource dataSource) {
//        final DatabaseDataSourceConnectionFactoryBean dbConnection
//            = new DatabaseDataSourceConnectionFactoryBean(dataSource);
//        dbConnection.setDatabaseConfig(dbUnitDatabaseConfig());
//        return dbConnection;
//    }
//
//    /**
//     * Get an entity manager factory to use for the tests.
//     *
//     * @param dataSource the data source to use
//     * @return The factory
//     */
//    @Bean
//    public LocalContainerEntityManagerFactoryBean entityManagerFactory(final DataSource dataSource) {
//        final LocalContainerEntityManagerFactoryBean thing = new LocalContainerEntityManagerFactoryBean();
//        thing.setDataSource(dataSource);
//        return thing;
//    }
//
//    /**
//     * Transaction manager.
//     *
//     * @param factory the factory to use
//     * @return The transaction manager
//     */
//    @Bean
//    public JpaTransactionManager transactionManager(final LocalContainerEntityManagerFactoryBean factory) {
//        final JpaTransactionManager transactionManager = new JpaTransactionManager();
//        transactionManager.setEntityManagerFactory(factory.nativeEntityManagerFactory);
//        return transactionManager;
//    }
}
