/*
 *
 *  Copyright 2018 Netflix, Inc.
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
package com.netflix.genie.agent.configs;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.AwsRegionProvider;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.regions.Regions;
import com.netflix.genie.agent.aws.s3.S3ClientFactory;
import com.netflix.genie.agent.aws.s3.S3ProtocolResolver;
import com.netflix.genie.agent.aws.s3.S3ProtocolResolverRegistrar;
import com.netflix.genie.agent.execution.services.ArchivalService;
import com.netflix.genie.agent.execution.services.impl.NoOpArchivalServiceImpl;
import com.netflix.genie.agent.execution.services.impl.S3ArchivalServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.aws.autoconfigure.context.ContextCredentialsAutoConfiguration;
import org.springframework.cloud.aws.autoconfigure.context.ContextInstanceDataAutoConfiguration;
import org.springframework.cloud.aws.autoconfigure.context.ContextRegionProviderAutoConfiguration;
import org.springframework.cloud.aws.autoconfigure.context.ContextResourceLoaderAutoConfiguration;
import org.springframework.cloud.aws.autoconfigure.context.ContextStackAutoConfiguration;
import org.springframework.cloud.aws.autoconfigure.context.properties.AwsRegionProperties;
import org.springframework.cloud.aws.autoconfigure.context.properties.AwsS3ResourceLoaderProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Spring Boot auto configuration for AWS related beans for the Genie Agent. Should be configured after all the
 * Spring Cloud AWS context configurations are complete.
 *
 * @author tgianos
 * @since 4.0.0
 */
@Configuration
@EnableConfigurationProperties
@AutoConfigureAfter(
    {
        ContextCredentialsAutoConfiguration.class,
        ContextInstanceDataAutoConfiguration.class,
        ContextRegionProviderAutoConfiguration.class,
        ContextResourceLoaderAutoConfiguration.class,
        ContextStackAutoConfiguration.class
    }
)
@ConditionalOnBean(AWSCredentialsProvider.class)
@Slf4j
public class AwsAutoConfiguration {

    /**
     * Get an AWS region provider instance. The rules for this basically follow what Spring Cloud AWS does but uses
     * the interface from the AWS SDK instead and provides a sensible default.
     * <p>
     * See: <a href="https://tinyurl.com/y9edl6yr">Spring Cloud AWS Region Documentation</a>
     *
     * @param awsRegionProperties The cloud.aws.region.* properties
     * @return A region provider based on whether static was set by user, else auto, else default of us-east-1
     */
    @Bean
    @ConditionalOnMissingBean(AwsRegionProvider.class)
    public AwsRegionProvider awsRegionProvider(final AwsRegionProperties awsRegionProperties) {
        final String staticRegion = awsRegionProperties.getStatic();
        if (StringUtils.isNotBlank(staticRegion)) {
            // Make sure we have a valid region. Will throw runtime exception if not.
            final Regions region = Regions.fromName(staticRegion);
            return new AwsRegionProvider() {
                /**
                 * Always return the static configured region.
                 *
                 * {@inheritDoc}
                 */
                @Override
                public String getRegion() throws SdkClientException {
                    return region.getName();
                }
            };
        } else if (awsRegionProperties.isAuto()) {
            return new DefaultAwsRegionProviderChain();
        } else {
            // Sensible default
            return new AwsRegionProvider() {
                /**
                 * Always default to us-east-1.
                 *
                 * {@inheritDoc}
                 */
                @Override
                public String getRegion() throws SdkClientException {
                    return Regions.US_EAST_1.getName();
                }
            };
        }
    }

    /**
     * Provide a lazy {@link S3ClientFactory} instance if one is needed by the system.
     *
     * @param awsCredentialsProvider The {@link AWSCredentialsProvider} to use
     * @param awsRegionProvider      The {@link AwsRegionProvider} to use
     * @param environment            The Spring application {@link Environment} to bind properties from
     * @return A {@link S3ClientFactory} instance
     */
    @Bean
    @ConditionalOnMissingBean(S3ClientFactory.class)
    public S3ClientFactory s3ClientFactory(
        final AWSCredentialsProvider awsCredentialsProvider,
        final AwsRegionProvider awsRegionProvider,
        final Environment environment
    ) {
        return new S3ClientFactory(awsCredentialsProvider, awsRegionProvider, environment);
    }

    /**
     * Provide a lazy S3 based {@link ArchivalService} bean if AWS credentials are present in the context.
     *
     * @param awsCredentialsProvider The credentials provider to use
     * @param s3ClientFactory        The {@link S3ClientFactory} to use to get clients for buckets
     * @return A {@link S3ArchivalServiceImpl} instance if credentials are valid else a {@link NoOpArchivalServiceImpl}
     */
    @Bean
    @Lazy
    public ArchivalService archivalService(
        final AWSCredentialsProvider awsCredentialsProvider,
        final S3ClientFactory s3ClientFactory
    ) {
        /*
         * TODO: Spring Cloud AWS always provides a credentials provider once it is on the classpath.
         *
         * For this reason this block exists to proactively verify that the credentials provided will be valid at
         * runtime in order to create a working S3 client later on. If the credentials don't work this will fall back
         * to creating a No Op Archival service implementation.
         *
         * Long term we should just have one ArchivalServiceImpl which uses the ResourceLoader and this won't be
         * necessary.
         */
        try {
            awsCredentialsProvider.getCredentials();
        } catch (final SdkClientException sdkClientException) {
            log.warn(
                "Attempted to validate AWS credentials and failed due to {}. Falling back to no op implementation",
                sdkClientException.getMessage(),
                sdkClientException
            );

            return new NoOpArchivalServiceImpl();
        }

        return new S3ArchivalServiceImpl(s3ClientFactory);
    }

    /**
     * Provide a configuration properties bean for Spring Cloud resource loader properties if for whatever reason
     * the {@link ContextResourceLoaderAutoConfiguration} isn't applied by the agent app.
     *
     * @return A {@link AwsS3ResourceLoaderProperties} instance with the bindings from cloud.aws.loader values
     */
    @Bean
    @ConditionalOnMissingBean(AwsS3ResourceLoaderProperties.class)
    @ConfigurationProperties(ContextResourceLoaderAutoConfiguration.AWS_LOADER_PROPERTY_PREFIX)
    public AwsS3ResourceLoaderProperties awsS3ResourceLoaderProperties() {
        return new AwsS3ResourceLoaderProperties();
    }

    /**
     * Provide an protocol resolver which will allow resources with s3:// prefixes to be resolved by the
     * application {@link org.springframework.core.io.ResourceLoader} provided this bean is eventually added to the
     * context via the
     * {@link org.springframework.context.ConfigurableApplicationContext#addProtocolResolver(ProtocolResolver)}
     * method.
     *
     * @param resourceLoaderProperties The {@link AwsS3ResourceLoaderProperties} instance to use
     * @param s3ClientFactory          The {@link S3ClientFactory} instance to use
     * @return A {@link S3ProtocolResolver} instance
     */
    @Bean
    @ConditionalOnMissingBean(S3ProtocolResolver.class)
    public S3ProtocolResolver s3ProtocolResolver(
        final AwsS3ResourceLoaderProperties resourceLoaderProperties,
        final S3ClientFactory s3ClientFactory
    ) {
        final ThreadPoolTaskExecutor s3TaskExecutor = new ThreadPoolTaskExecutor();
        s3TaskExecutor.setCorePoolSize(resourceLoaderProperties.getCorePoolSize());
        s3TaskExecutor.setMaxPoolSize(resourceLoaderProperties.getMaxPoolSize());
        s3TaskExecutor.setQueueCapacity(resourceLoaderProperties.getQueueCapacity());
        s3TaskExecutor.setThreadGroupName("Genie-S3-Resource-Loader-Thread-Pool");
        s3TaskExecutor.setThreadNamePrefix("S3-resource-loader-thread");
        return new S3ProtocolResolver(s3ClientFactory, s3TaskExecutor);
    }

    /**
     * Configurer bean which will add the {@link S3ProtocolResolver} to the set of {@link ProtocolResolver} in the
     * application context.
     *
     * @param s3ProtocolResolver The implementatin of {@link S3ProtocolResolver} to use
     * @return A {@link S3ProtocolResolverRegistrar} instance
     */
    @Bean
    @ConditionalOnMissingBean(S3ProtocolResolverRegistrar.class)
    public S3ProtocolResolverRegistrar s3ProtocolResolverRegistrar(final S3ProtocolResolver s3ProtocolResolver) {
        return new S3ProtocolResolverRegistrar(s3ProtocolResolver);
    }
}
