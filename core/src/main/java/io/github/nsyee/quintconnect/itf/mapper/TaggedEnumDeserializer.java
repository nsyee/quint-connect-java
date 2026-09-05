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
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;

/**
 * Lets a Java enum accept a tag-only Quint sum type: {@code { tag: "X", value: () }} is mapped like
 * the plain string {@code "X"}. A variant carrying a value is rejected.
 */
final class TaggedEnumDeserializer extends DelegatingDeserializer {

  private static final long serialVersionUID = 1L;

  TaggedEnumDeserializer(JsonDeserializer<?> delegate) {
    super(delegate);
  }

  @Override
  protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegatee) {
    return new TaggedEnumDeserializer(newDelegatee);
  }

  @Override
  public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    if (!p.isExpectedStartObjectToken()) {
      return _delegatee.deserialize(p, ctxt);
    }
    JsonNode node = ctxt.readTree(p);
    if (!ItfNodes.isVariantNode(node)) {
      throw JsonMappingException.from(
          p,
          "Cannot map "
              + node
              + " to enum "
              + handledType().getName()
              + ": expected a variant record `{ tag, value }`");
    }
    JsonNode value = node.get("value");
    if (value != null && !(value.isArray() && value.isEmpty())) {
      throw JsonMappingException.from(
          p,
          "Variant `"
              + node.get("tag").textValue()
              + "` carries a value but enum "
              + handledType().getName()
              + " cannot hold one; use a sealed interface instead");
    }
    return Delegation.deserialize(
        _delegatee, JsonNodeFactory.instance.textNode(node.get("tag").textValue()), p, ctxt);
  }
}
