/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */package org.apache.sling.models.persist.bean;

import javax.inject.Inject;
import org.apache.sling.models.annotations.Path;

/**
 * Example of bean with getPath method that stores the path in a field using an annotation marker
 */
public class BeanWithAnnotatedPathGetter {
    @Inject
    public String prop1 = "testValue";

    public String path = "/test/WRONG-path";

    public String correctPath = "/test/annotated-getter-path";

    public String getPath() {
        return path;
    }

    @Path
    public String getCorrectPath() {
        return path;
    }
}
