/*
 * Copyright (c) Bosch Software Innovations GmbH 2016-2017.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.antenna.workflow.stubs;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.eclipse.sw360.antenna.api.IEvaluationResult;
import org.eclipse.sw360.antenna.api.IPolicyEvaluation;
import org.eclipse.sw360.antenna.api.IProcessingReporter;
import org.eclipse.sw360.antenna.api.workflow.AbstractProcessor;
import org.eclipse.sw360.antenna.api.workflow.WorkflowStepResult;
import org.eclipse.sw360.antenna.model.artifact.Artifact;
import org.eclipse.sw360.antenna.model.coordinates.Coordinate;
import org.eclipse.sw360.antenna.model.reporting.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractComplianceChecker extends AbstractProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractComplianceChecker.class);
    private IEvaluationResult.Severity failOn;
    protected static final String FAIL_ON_KEY = "failOn";
    private Set<IEvaluationResult> evaluationResults = Collections.emptySet();

    public AbstractComplianceChecker() {
        this.workflowStepOrder = VALIDATOR_BASE_ORDER;
    }

    public abstract IPolicyEvaluation evaluate(Collection<Artifact> artifacts);

    public abstract String getRulesetDescription();

    @Override
    public Collection<Artifact> process(Collection<Artifact> artifacts) {
        LOGGER.info("Evaluate compliance rule set: {}", getRulesetDescription());
        IPolicyEvaluation evaluation = evaluate(artifacts);
        LOGGER.info("Rule evaluation done");
        LOGGER.info("Check evaluation results...");
        execute(evaluation);
        LOGGER.info("Check evaluation results... done.");
        return artifacts;
    }

    @Override
    public WorkflowStepResult postProcessResult(WorkflowStepResult result) {
        WorkflowStepResult pResult = super.postProcessResult(result);
        if (evaluationResults.size() > 0) {
            pResult.addFailCausingResults(getWorkflowItemName(), evaluationResults);
        }
        return pResult;
    }

    @Override
    public void configure(Map<String, String> configMap) {
        failOn = getSeverityFromConfig(FAIL_ON_KEY, configMap, IEvaluationResult.Severity.FAIL);
    }

    @SuppressFBWarnings("SF_SWITCH_FALLTHROUGH")
    public void execute(IPolicyEvaluation evaluation) {
        IProcessingReporter reporter = context.getProcessingReporter();

        reportResults(reporter, evaluation.getEvaluationResults());

        Set<IEvaluationResult> failResults = getResults(evaluation, IEvaluationResult.Severity.FAIL);
        Set<IEvaluationResult> warnResults = getResults(evaluation, IEvaluationResult.Severity.WARN);
        Set<IEvaluationResult> infoResults = getResults(evaluation, IEvaluationResult.Severity.INFO);

        Set<IEvaluationResult> failCausingResults = new HashSet<>();

        /*
         * The severity is ordered: INFO < WARN < FAIL If the user specified
         * <entry key="failOn" value="INFO"/> the build will fail on INFO, WARN and FAIL If
         * the user specified <entry key="failOn" value="WARN"/> the build will fail on WARN
         * and FAIL If the user specified <entry key="failOn" value="WARN"/> the build will
         * only fail on FAIL (this is the default)
         */
        switch (failOn.value()) {
            case "INFO": failCausingResults.addAll(infoResults);
            case "WARN": failCausingResults.addAll(warnResults);
            case "FAIL": failCausingResults.addAll(failResults);
        }

        // Flag the build as failed if the report engine reports it as so.
        if (failCausingResults.size() > 0) {
            String messagePrefix = "Rule engine=[" + getRulesetDescription() + "] failed evaluation.";
            String fullMessage = makeStringForEvaluationResults(messagePrefix, failCausingResults);
            reporter.add(MessageType.PROCESSING_FAILURE, fullMessage);
            LOGGER.info(fullMessage);
            this.evaluationResults = failCausingResults;
        }
    }

    protected String makeStringForEvaluationResults(String messagePrefix, Set<IEvaluationResult> failCausingResults) {
        Map<String,Set<IEvaluationResult>> transposedFailCausingResults = new HashMap<>();
        failCausingResults.forEach(iEvaluationResult ->
                iEvaluationResult.getFailedArtifacts().forEach(artifact -> {
                            String artifactRepresentation = artifact.toString();
                            if(!transposedFailCausingResults.containsKey(artifactRepresentation)) {
                                transposedFailCausingResults.put(artifactRepresentation, new HashSet<>());
                            }
                            transposedFailCausingResults.get(artifactRepresentation).add(iEvaluationResult);
                        }
                ));

        String msges = transposedFailCausingResults.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> makeStringForEvaluationResultsForArtifact(entry.getKey(), entry.getValue()))
                .limit(3)
                .reduce("", (s1,s2) -> s1 + s2);
        if (transposedFailCausingResults.size() > 3) {
            msges += "\n\t - ... and " + (transposedFailCausingResults.size() - 3) + " artifacts more";
        }
        String header = messagePrefix + " Due to:";
        return header + msges + "\nSee generated report for details.";
    }

    protected String makeStringForEvaluationResultsForArtifact(String artifactRepresentation, Set<IEvaluationResult> failCausingResultsForArtifact) {
        String msg = failCausingResultsForArtifact.stream()
                .map(iEvaluationResult -> "\n\t\t- " + iEvaluationResult.getDescription())
                .sorted()
                .limit(3)
                .reduce("\n\t- the artifact=[" + artifactRepresentation + "] failed, due to:", (s1,s2) -> s1 + s2);
        if (failCausingResultsForArtifact.size() > 3) {
            return msg + "\n\t\t- ... and " + (failCausingResultsForArtifact.size() - 3) + " fail causing results more";
        }
        return msg;
    }

    private Set<IEvaluationResult> getResults(IPolicyEvaluation evaluation, IEvaluationResult.Severity level) {
        return evaluation.getEvaluationResults().stream()
                .filter(r -> r.getSeverity() == level && r.getFailedArtifacts().size() > 0).collect(Collectors.toSet());
    }

    private void reportResults(IProcessingReporter reporter, Set<IEvaluationResult> results) {
        results.stream()
                .sorted(Comparator.comparing(IEvaluationResult::getId))
                .forEach(result -> reportSingleResult(reporter, result));
        }

    private void reportSingleResult(IProcessingReporter reporter, IEvaluationResult result) {
        result.getFailedArtifacts().forEach(a -> reporter.add(MessageType.RULE_ENGINE,
                result.getId() + " (" + result.getSeverity() + "): "
                        + getCoordinates(a) + " : " + result.getDescription()));
    }

    private String getCoordinates(Artifact a) {
        Optional<Coordinate> coordinate = a.getCoordinateForType(Coordinate.Types.MAVEN);
        if (! coordinate.isPresent()) {
            coordinate = a.getCoordinates().stream().findFirst();
        }
        return coordinate.map(Coordinate::canonicalize)
                .orElse(a.prettyPrint());
    }

    protected IEvaluationResult.Severity getSeverityFromConfig(String key, Map<String, String> configMap, IEvaluationResult.Severity defaultSeverity) {
        return Optional.ofNullable(configMap.get(key))
                .map(IEvaluationResult.Severity::fromValue)
                .orElse(defaultSeverity);
    }
}
