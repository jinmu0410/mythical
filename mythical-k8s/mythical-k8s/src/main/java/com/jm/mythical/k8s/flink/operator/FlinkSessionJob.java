/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jm.mythical.k8s.flink.operator;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jm.mythical.k8s.flink.operator.spec.FlinkSessionJobSpec;
import com.jm.mythical.k8s.flink.operator.status.FlinkSessionJobStatus;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import org.apache.flink.annotation.Experimental;


/**
 * Custom resource definition that represents a flink session job.
 */
@Experimental
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize()
@Group(org.apache.flink.kubernetes.operator.api.CrdConstants.API_GROUP)
@Version(CrdConstants.API_VERSION)
@ShortNames({"sessionjob"})
public class FlinkSessionJob
        extends AbstractFlinkResource<FlinkSessionJobSpec, FlinkSessionJobStatus>
        implements Namespaced {

    @Override
    protected FlinkSessionJobStatus initStatus() {
        return new FlinkSessionJobStatus();
    }

    @Override
    public FlinkSessionJobSpec initSpec() {
        return new FlinkSessionJobSpec();
    }
}
