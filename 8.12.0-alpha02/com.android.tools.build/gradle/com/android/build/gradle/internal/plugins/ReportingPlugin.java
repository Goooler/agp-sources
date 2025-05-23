/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.plugins;

import java.util.stream.Stream;

import static com.android.builder.core.BuilderConstants.FD_ANDROID_RESULTS;
import static com.android.builder.core.BuilderConstants.FD_ANDROID_TESTS;
import static com.android.builder.core.BuilderConstants.FD_REPORTS;

import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.internal.errors.AndroidProblemReporterProvider;
import com.android.build.gradle.internal.errors.DeprecationReporterImpl;
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl;
import com.android.build.gradle.internal.lint.LintFromMaven;
import com.android.build.gradle.internal.scope.ProjectInfo;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.DslServicesImpl;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.tasks.AndroidReportTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask;
import com.android.build.gradle.internal.tasks.ManagedDeviceTestTask;
import com.android.build.gradle.internal.test.report.ReportType;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptionService;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.SyncOptions;
import com.android.utils.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;

/**
 * Gradle plugin class for 'reporting' projects.
 *
 * This is mostly used to aggregate reports from subprojects.
 *
 */
class ReportingPlugin implements org.gradle.api.Plugin<Project> {

    private TestOptions extension;


    @Override
    public void apply(final Project project) {
        project.getLogger()
                .warn("android-reporting plugin is deprecated and is set to be removed in AGP 9.0");
        // make sure this project depends on the evaluation of all sub projects so that
        // it's evaluated last.
        project.evaluationDependsOnChildren();

        ProjectOptions projectOptions =
                new ProjectOptionService.RegistrationAction(project)
                        .execute()
                        .get()
                        .getProjectOptions();
        AndroidProblemReporterProvider androidProblemReporterProvider
                = new AndroidProblemReporterProvider.RegistrationAction(project, projectOptions)
                        .execute()
                        .get();
        SyncIssueReporterImpl syncIssueHandler =
                new SyncIssueReporterImpl(
                        SyncOptions.getModelQueryMode(projectOptions),
                        SyncOptions.getErrorFormatMode(projectOptions),
                        project.getLogger(),
                        androidProblemReporterProvider.reporter()
                );


        DeprecationReporterImpl deprecationReporter =
                new DeprecationReporterImpl(syncIssueHandler, projectOptions, project.getPath());
        LintFromMaven lintFromMaven = LintFromMaven.from(project, projectOptions, syncIssueHandler);

        ProjectServices projectServices =
                new ProjectServices(
                        syncIssueHandler,
                        deprecationReporter,
                        project.getObjects(),
                        project.getLogger(),
                        project.getProviders(),
                        project.getLayout(),
                        projectOptions,
                        project.getGradle().getSharedServices(),
                        lintFromMaven,
                        null,
                        project.getGradle().getStartParameter().getMaxWorkerCount(),
                        new ProjectInfo(project),
                        project::file,
                        project.getConfigurations(),
                        project.getDependencies(),
                        project.getExtensions().getExtraProperties(),
                        project.getTasks()::register,
                        project.getPluginManager());

        DslServices dslServices =
                new DslServicesImpl(
                        projectServices, project.getProviders().provider(() -> null), null, null);

        extension = project.getExtensions().create("android", TestOptions.class, dslServices);

        final AndroidReportTask mergeReportsTask = project.getTasks().create("mergeAndroidReports",
                AndroidReportTask.class);
        mergeReportsTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        mergeReportsTask.setDescription("Merges all the Android test reports from the sub "
                + "projects.");
        mergeReportsTask.setReportType(ReportType.MULTI_PROJECT);

        mergeReportsTask
                .getResultsDir()
                .set(
                        project.provider(
                                () -> {
                                    String resultsDir = extension.getResultsDir();
                                    if (resultsDir == null) {
                                        return project.getLayout()
                                                .getBuildDirectory()
                                                .dir(FD_ANDROID_RESULTS)
                                                .get();
                                    } else {
                                        return project.getLayout()
                                                .getProjectDirectory()
                                                .dir(resultsDir);
                                    }
                                }));

        mergeReportsTask
                .getReportsDir()
                .set(
                        project.provider(
                                () -> {
                                    String reportsDir = extension.getReportDir();
                                    if (reportsDir == null) {
                                        return project.getLayout()
                                                .getBuildDirectory()
                                                .dir(FileUtils.join(FD_REPORTS, FD_ANDROID_TESTS))
                                                .get();
                                    } else {
                                        return project.getLayout()
                                                .getProjectDirectory()
                                                .dir(reportsDir);
                                    }
                                }));

        // gather the subprojects
        project.afterEvaluate(
                prj -> {
                    boolean includeManagedDevices =
                            projectOptions.get(
                                    BooleanOption
                                            .GRADLE_MANAGED_DEVICE_INCLUDE_MANAGED_DEVICES_IN_REPORTING);

                    for (Project p : prj.getSubprojects()) {

                        Stream.of(
                                        p.getTasks().withType(AndroidReportTask.class),
                                        p.getTasks()
                                                .withType(DeviceProviderInstrumentTestTask.class),
                                        includeManagedDevices
                                                ? p.getTasks()
                                                        .withType(
                                                                ManagedDeviceInstrumentationTestTask
                                                                        .class)
                                                : null,
                                        includeManagedDevices
                                                ? p.getTasks().withType(ManagedDeviceTestTask.class)
                                                : null)
                                .filter(collection -> collection != null)
                                .flatMap(collection -> collection.stream())
                                .forEach(task -> mergeReportsTask.addTask(task));
                    }
                });

        // If gradle is launched with --continue, we want to run all tests and generate an
        // aggregate report (to help with the fact that we may have several build variants).
        // To do that, the "mergeAndroidReports" task (which does the aggregation) must always
        // run even if one of its dependent task (all the testFlavor tasks) fails, so we make
        // them ignore their error.
        // We cannot do that always: in case the test task is not going to run, we do want the
        // individual testFlavor tasks to fail.
        if (project.getGradle().getStartParameter().isContinueOnFailure()) {
            project.getGradle().getTaskGraph().whenReady(taskExecutionGraph -> {
                if (taskExecutionGraph.hasTask(mergeReportsTask)) {
                    mergeReportsTask.setWillRun();
                }
            });
        }
    }
}
