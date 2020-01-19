/*
 * Copyright (c) Bosch Software Innovations GmbH 2018.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.antenna.sw360.rest.resource.releases;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.eclipse.sw360.antenna.sw360.rest.resource.attachments.SW360AttachmentListEmbedded;
import org.eclipse.sw360.antenna.sw360.rest.resource.attachments.SW360SparseAttachment;
import org.eclipse.sw360.antenna.sw360.rest.resource.licenses.SW360LicenseListEmbedded;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@JsonDeserialize(as = SW360ReleaseEmbedded.class)
public class SW360ReleaseEmbedded extends SW360LicenseListEmbedded {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("sw360:attachments")
    private List<SW360SparseAttachment> attachments;

    public List<SW360SparseAttachment> getAttachments() {
        return Optional.ofNullable(attachments)
                .map(ArrayList::new)
                .orElse(new ArrayList<>());
    }

    public SW360AttachmentListEmbedded setAttachments(List<SW360SparseAttachment> attachments) {
        this.attachments = attachments;
        return new SW360AttachmentListEmbedded().setAttachments(attachments);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SW360ReleaseEmbedded that = (SW360ReleaseEmbedded) o;
        return Objects.equals(attachments, that.attachments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), attachments);
    }
}
