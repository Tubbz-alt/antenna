/*
 * Copyright (c) Bosch Software Innovations GmbH 2019.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.antenna.sw360.rest.utils;

import org.eclipse.sw360.antenna.api.exceptions.ExecutionException;
import org.eclipse.sw360.antenna.model.artifact.Artifact;
import org.eclipse.sw360.antenna.model.artifact.ArtifactCoordinates;
import org.eclipse.sw360.antenna.model.artifact.facts.ArtifactFilename;
import org.eclipse.sw360.antenna.model.coordinates.Coordinate;
import org.eclipse.sw360.antenna.sw360.rest.resource.components.SW360Component;
import org.eclipse.sw360.antenna.sw360.rest.resource.components.SW360ComponentType;
import org.eclipse.sw360.antenna.sw360.utils.SW360ComponentAdapterUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class SW360ComponentAdapterUtilsTest {
    private Artifact artifact;
    private String expectedName;
    private SW360ComponentType expectedType;
    private Class<? extends Exception> expectedException;
    private String expectedExceptionMsg;

    public SW360ComponentAdapterUtilsTest(Artifact artifact, String expectedName, SW360ComponentType expectedType,
                                          Class<? extends Exception> expectedException, String expectedExceptionMsg) {
        this.artifact = artifact;
        this.expectedName = expectedName;
        this.expectedType = expectedType;
        this.expectedException = expectedException;
        this.expectedExceptionMsg = expectedExceptionMsg;

    }

    @Parameterized.Parameters(name = "{index}: input={0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {
                    new Artifact()
                            .setProprietary(true)
                            .addFact(new ArtifactCoordinates(
                                    new Coordinate(Coordinate.Types.MAVEN, "org.example", "lib", "1.0"))),
                        "org.example/lib",
                        SW360ComponentType.INTERNAL,
                        null, null
                },
                {
                    new Artifact()
                            .setProprietary(false)
                            .addFact(new ArtifactCoordinates(
                                    new Coordinate(Coordinate.Types.NPM, "@organisation", "framework", "1.0"))),
                        "@organisation/framework",
                        SW360ComponentType.OSS,
                        null, null
                },
                {
                    new Artifact()
                            .setProprietary(false)
                            .addFact(new ArtifactCoordinates(
                                    new Coordinate(Coordinate.Types.NUGET, "Org.Example", "Library", "1.0"))),
                        "Org.Example/Library",
                        SW360ComponentType.OSS,
                        null, null
                },
                {
                    new Artifact()
                        .setProprietary(false)
                        .addFact(new ArtifactFilename("library-filename.ext")),
                        "library-filename.ext",
                        SW360ComponentType.OSS,
                        null, null
                },
                {
                    new Artifact()
                        .setProprietary(false),
                        null,
                        SW360ComponentType.OSS,
                        ExecutionException.class, "does not have enough facts to create a component name."
                }
        });
    }

    @Rule
    public ExpectedException thrownException = ExpectedException.none();

    @Test
    public void testCreateComponentFromArtifact() {
        SW360Component component = new SW360Component();

        if (expectedException != null) {
            thrownException.expect(expectedException);
            thrownException.expectMessage(expectedExceptionMsg);
        }

        SW360ComponentAdapterUtils.prepareComponent(component, artifact);

        assertThat(component.getName()).isEqualTo(expectedName);
        assertThat(component.getComponentType()).isEqualTo(expectedType);
    }
}
