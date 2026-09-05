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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.reflect.RecordComponent;

/**
 * Lets a Java record accept a Quint tuple: a JSON array with as many entries as the record has
 * components is mapped positionally. Objects are handled by Jackson's record support as usual.
 */
final class RecordTupleDeserializer extends DelegatingDeserializer {

  private static final long serialVersionUID = 1L;

  private final Class<?> recordClass;

  RecordTupleDeserializer(JsonDeserializer<?> delegate, Class<?> recordClass) {
    super(delegate);
    this.recordClass = recordClass;
  }

  @Override
  protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegatee) {
    return new RecordTupleDeserializer(newDelegatee, recordClass);
  }

  @Override
  public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    if (!p.isExpectedStartArrayToken()) {
      return _delegatee.deserialize(p, ctxt);
    }
    JsonNode array = ctxt.readTree(p);
    RecordComponent[] components = recordClass.getRecordComponents();
    if (array.size() != components.length) {
      throw JsonMappingException.from(
          p,
          "Cannot map a tuple with "
              + array.size()
              + " entries to "
              + recordClass.getName()
              + ", which has "
              + components.length
              + " components");
    }
    return Delegation.deserialize(_delegatee, tupleToObject(array, components), p, ctxt);
  }

  static ObjectNode tupleToObject(JsonNode array, RecordComponent[] components) {
    ObjectNode object = JsonNodeFactory.instance.objectNode();
    for (int i = 0; i < components.length; i++) {
      object.set(Variants.propertyName(components[i]), array.get(i));
    }
    return object;
  }
}
