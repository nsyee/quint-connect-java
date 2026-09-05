/*
 * Copyright 2026 the quint-connect-java authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.nsyee.quintconnect.itf.mapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.Optional;

/**
 * Maps a Quint {@code Option} to {@link Optional}: {@code Some(x)} → {@code Optional.of(x)}, {@code
 * None} → {@code Optional.empty()}. Any other value is wrapped as present, like {@code
 * ItfValues.asOption}, and a missing field yields {@code Optional.empty()}.
 */
final class OptionalDeserializer extends StdDeserializer<Optional<?>> {

  private static final long serialVersionUID = 1L;

  private final JavaType contentType;

  OptionalDeserializer(JavaType type, JavaType contentType) {
    super(type);
    this.contentType = contentType;
  }

  @Override
  public Optional<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = ctxt.readTree(p);
    if (ItfNodes.isVariantNode(node)) {
      String tag = node.get("tag").textValue();
      if (tag.equals("None")) {
        return Optional.empty();
      }
      if (tag.equals("Some")) {
        JsonNode value = node.get("value");
        if (value == null) {
          return Optional.empty();
        }
        return Optional.of(ctxt.readTreeAsValue(value, contentType));
      }
    }
    return Optional.of(ctxt.readTreeAsValue(node, contentType));
  }

  @Override
  public Optional<?> getNullValue(DeserializationContext ctxt) {
    return Optional.empty();
  }

  @Override
  public Object getAbsentValue(DeserializationContext ctxt) {
    return Optional.empty();
  }
}
