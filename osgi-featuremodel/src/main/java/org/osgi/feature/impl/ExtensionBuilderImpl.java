/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.osgi.feature.impl;

import org.osgi.feature.ArtifactID;
import org.osgi.feature.Extension;
import org.osgi.feature.Extension.Kind;
import org.osgi.feature.Extension.Type;
import org.osgi.feature.ExtensionBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class ExtensionBuilderImpl implements ExtensionBuilder {
    private final String name;
    private final Type type;
    private final Kind kind;

    private final StringBuilder content = new StringBuilder();

    ExtensionBuilderImpl(String name, Type type, Kind kind) {
        this.name = name;
        this.type = type;
        this.kind = kind;
    }

    @Override
    public ExtensionBuilder addText(String text) {
        if (type != Type.TEXT)
            throw new IllegalStateException("Cannot add text to extension of type " + type);

        content.append(text);
        return this;
    }

    @Override
    public ExtensionBuilder setJSON(String json) {
        if (type != Type.JSON)
            throw new IllegalStateException("Cannot add text to extension of type " + type);

        content.setLength(0); // Clear any previous value
        content.append(json);
        return this;
    }

    @Override
    public ExtensionBuilder addArtifact(ArtifactID aid) {
        addArtifact(aid.getGroupId(), aid.getArtifactId(), aid.getVersion(), aid.getType(), aid.getClassifier());
        return this;
    }

    @Override
    public ExtensionBuilder addArtifact(String groupId, String artifactId, String version) {
        return addArtifact(groupId, artifactId, version, null, null);
    }

    @Override
    public ExtensionBuilder addArtifact(String groupId, String artifactId, String version, String at, String classifier) {
        if (type != Type.ARTIFACTS)
            throw new IllegalStateException("Cannot add artifacts to extension of type " + type);

        content.append(groupId);
        content.append(':');
        content.append(artifactId);
        content.append(':');
        content.append(version);

        if (at != null) {
            content.append(':');
            content.append(at);
            if (classifier != null) {
                content.append(':');
                content.append(classifier);
            }
        }
        content.append('\n');
        return this;
    }

    @Override
    public Extension build() {
        return new ExtensionImpl(name, type, kind, content.toString());
    }

    private static class ExtensionImpl implements Extension {
        private final String name;
        private final Type type;
        private final Kind kind;
        private final String content;

        private ExtensionImpl(String name, Type type, Kind kind, String content) {
            this.name = name;
            this.type = type;
            this.kind = kind;
            this.content = content;
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public Kind getKind() {
            return kind;
        }

        public String getJSON() {
            if (type != Type.JSON)
                throw new IllegalStateException("Extension is not of type JSON " + type);

            return content;
        }

        public String getText() {
            if (type != Type.TEXT)
                throw new IllegalStateException("Extension is not of type Text " + type);

            return content;
        }

        public List<ArtifactID> getArtifacts() {
            BufferedReader r = new BufferedReader(new StringReader(content));

            List<ArtifactID> res = new ArrayList<>();
            String line = null;
            try {
                while ((line = r.readLine()) != null) {
                    res.add(ArtifactID.fromMavenID(line));
                }
            } catch (IOException e) {
                // ignore
            }

            return res;
        }

        @Override
        public int hashCode() {
            return Objects.hash(content, name, type);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof ExtensionImpl))
                return false;
            ExtensionImpl other = (ExtensionImpl) obj;
            return Objects.equals(content, other.content) && Objects.equals(name, other.name) && type == other.type;
        }

        @Override
        public String toString() {
            return "ExtensionImpl [name=" + name + ", type=" + type + "]";
        }
    }
}
