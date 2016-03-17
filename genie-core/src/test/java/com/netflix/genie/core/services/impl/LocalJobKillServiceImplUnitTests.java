/*
 *
 *  Copyright 2016 Netflix, Inc.
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
package com.netflix.genie.core.services.impl;

import com.netflix.genie.common.dto.JobExecution;
import com.netflix.genie.common.exceptions.GenieException;
import com.netflix.genie.common.exceptions.GeniePreconditionException;
import com.netflix.genie.common.exceptions.GenieServerException;
import com.netflix.genie.core.services.JobSearchService;
import com.netflix.genie.test.categories.UnitTest;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.UUID;

/**
 * Unit tests for the LocalJobKillServiceImpl class.
 *
 * @author tgianos
 * @since 3.0.0
 */
@Category(UnitTest.class)
public class LocalJobKillServiceImplUnitTests {

    private static final String ID = UUID.randomUUID().toString();
    private static final String HOSTNAME = UUID.randomUUID().toString();
    private static final int PID = 18243;
    private CommandLine psCommand;
    private CommandLine killCommand;
    private JobSearchService jobSearchService;
    private Executor executor;
    private LocalJobKillServiceImpl service;

    /**
     * Setup for the tests.
     */
    @Before
    public void setup() {
        Assume.assumeTrue(SystemUtils.IS_OS_UNIX);
        this.jobSearchService = Mockito.mock(JobSearchService.class);
        this.executor = Mockito.mock(Executor.class);
        this.service = new LocalJobKillServiceImpl(HOSTNAME, this.jobSearchService, this.executor);

        this.killCommand = new CommandLine("kill");
        this.killCommand.addArguments(Integer.toString(PID));

        this.psCommand = new CommandLine("ps");
        this.psCommand.addArgument("-p");
        this.psCommand.addArgument(Integer.toString(PID));
    }

    /**
     * Make sure we don't execute any functionality if the job is already not running.
     *
     * @throws GenieException on any error
     */
    @Test
    public void wontKillJobIfAlreadyNotRunning() throws GenieException {
        final JobExecution jobExecution = Mockito.mock(JobExecution.class);
        Mockito.when(jobExecution.getExitCode()).thenReturn(JobExecution.DEFAULT_EXIT_CODE + 1);
        Mockito.when(this.jobSearchService.getJobExecution(ID)).thenReturn(jobExecution);

        this.service.killJob(ID);
    }

    /**
     * Make sure we throw an exception if the job isn't actually running on this host.
     *
     * @throws GenieException On error
     */
    @Test(expected = GeniePreconditionException.class)
    public void cantKillJobIfNotOnThisHost() throws GenieException {
        final JobExecution jobExecution = Mockito.mock(JobExecution.class);
        Mockito.when(jobExecution.getExitCode()).thenReturn(JobExecution.DEFAULT_EXIT_CODE);
        Mockito.when(jobExecution.getHostname()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(this.jobSearchService.getJobExecution(ID)).thenReturn(jobExecution);

        this.service.killJob(ID);
    }

    /**
     * Make sure that if between the time the job execution was pulled from the database and now the job didn't finish.
     *
     * @throws GenieException on any error
     * @throws IOException    on error in execute
     */
    @Test
    public void cantKillJobIfAlreadyDoneSinceDBCall() throws GenieException, IOException {
        final JobExecution jobExecution = Mockito.mock(JobExecution.class);
        Mockito.when(jobExecution.getExitCode()).thenReturn(JobExecution.DEFAULT_EXIT_CODE);
        Mockito.when(jobExecution.getHostname()).thenReturn(HOSTNAME);
        Mockito.when(jobExecution.getProcessId()).thenReturn(PID);
        Mockito.when(this.jobSearchService.getJobExecution(ID)).thenReturn(jobExecution);
        Mockito.when(this.executor.execute(Mockito.any(CommandLine.class))).thenThrow(new ExecuteException("blah", 1));

        this.service.killJob(ID);

        Mockito.verify(this.executor, Mockito.never()).execute(this.killCommand);
    }

    /**
     * Make sure that if between the time the job execution was pulled from the database and now the job didn't finish.
     *
     * @throws GenieException on any error
     * @throws IOException    on error in execute
     */
    @Test(expected = GenieServerException.class)
    public void cantKillJobIfCantCheckProcessStatus() throws GenieException, IOException {
        final JobExecution jobExecution = Mockito.mock(JobExecution.class);
        Mockito.when(jobExecution.getExitCode()).thenReturn(JobExecution.DEFAULT_EXIT_CODE);
        Mockito.when(jobExecution.getHostname()).thenReturn(HOSTNAME);
        Mockito.when(jobExecution.getProcessId()).thenReturn(PID);
        Mockito.when(this.jobSearchService.getJobExecution(ID)).thenReturn(jobExecution);
        Mockito.when(this.executor.execute(Mockito.any(CommandLine.class))).thenThrow(new IOException());

        this.service.killJob(ID);

        Mockito.verify(this.executor, Mockito.never()).execute(this.killCommand);
    }

    /**
     * Make sure that if we can't kill the actual process it throws an exception.
     *
     * @throws GenieException on any error
     * @throws IOException    on error in execute
     */
    @Test(expected = GenieServerException.class)
    public void cantKillJobIfCantKillProcess() throws GenieException, IOException {
        final JobExecution jobExecution = Mockito.mock(JobExecution.class);
        Mockito.when(jobExecution.getExitCode()).thenReturn(JobExecution.DEFAULT_EXIT_CODE);
        Mockito.when(jobExecution.getHostname()).thenReturn(HOSTNAME);
        Mockito.when(jobExecution.getProcessId()).thenReturn(PID);
        Mockito.when(this.jobSearchService.getJobExecution(ID)).thenReturn(jobExecution);
        Mockito.when(this.executor.execute(Mockito.any(CommandLine.class))).thenReturn(0).thenThrow(new IOException());

        this.service.killJob(ID);
        Mockito.verify(this.executor, Mockito.times(1)).execute(this.killCommand);
    }

    /**
     * Make sure we can kill a job.
     *
     * @throws GenieException On any error
     * @throws IOException    On error in execute
     */
    @Test
    public void canKillJob() throws GenieException, IOException {
        final JobExecution jobExecution = Mockito.mock(JobExecution.class);
        Mockito.when(jobExecution.getExitCode()).thenReturn(JobExecution.DEFAULT_EXIT_CODE);
        Mockito.when(jobExecution.getHostname()).thenReturn(HOSTNAME);
        Mockito.when(jobExecution.getProcessId()).thenReturn(PID);
        Mockito.when(this.jobSearchService.getJobExecution(ID)).thenReturn(jobExecution);
        Mockito.when(this.executor.execute(Mockito.any(CommandLine.class))).thenReturn(0, 0);

        this.service.killJob(ID);

        Mockito.verify(this.executor, Mockito.times(2)).execute(Mockito.any(CommandLine.class));
    }
}
