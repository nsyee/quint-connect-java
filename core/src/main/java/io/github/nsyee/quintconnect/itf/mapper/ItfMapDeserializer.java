/*
 * Copyright 2025 the quint-connect-java authors
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
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.type.MapType;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lets {@code Map<K, V>} accept the {@code {"#map": [[k, v], …]}} encoding of a Quint map with
 * non-string keys: every key is deserialised as {@code K} (not through a Jackson {@code
 * KeyDeserializer}) and every value as {@code V}.
 *
 * <p>Plain objects are handed to Jackson's own map deserializer when the key type has a {@code
 * KeyDeserializer} (strings, numbers, enums, …); for structured keys such as records only the
 * {@code #map} encoding is accepted, because JSON object keys cannot express them.
 */
final class ItfMapDeserializer extends StdDeserializer<Map<Object, Object>>
    implements ResolvableDeserializer, ContextualDeserializer {

  private static final long serialVersionUID = 1L;

  private final MapType type;
  private final transient JsonDeserializer<?> delegate;

  ItfMapDeserializer(JsonDeserializer<?> delegate, MapType type) {
    super(type);
    this.type = type;
    this.delegate = hasSimpleKey(type) ? delegate : null;
  }

  private static boolean hasSimpleKey(MapType type) {
    Class<?> key = type.getKeyType().getRawClass();
    return key == String.class
        || key == Object.class
        || key.isEnum()
        || key.isPrimitive()
        || Number.class.isAssignableFrom(key)
        || key == Boolean.class
        || key == Character.class
        || CharSequence.class.isAssignableFrom(key);
  }

  @Override
  public void resolve(DeserializationContext ctxt) throws JsonMappingException {
    if (delegate instanceof ResolvableDeserializer resolvable) {
      resolvable.resolve(ctxt);
    }
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
      throws JsonMappingException {
    if (delegate instanceof ContextualDeserializer contextual) {
      JsonDeserializer<?> resolved = contextual.createContextual(ctxt, property);
      return new ItfMapDeserializer(resolved, type);
    }
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<Object, Object> deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    JsonNode node = ctxt.readTree(p);
    if (!ItfNodes.isMapNode(node)) {
      if (delegate == null) {
        throw JsonMappingException.from(
            p,
            "Cannot map "
                + node
                + " to "
                + type.toCanonical()
                + ": keys of type "
                + type.getKeyType().toCanonical()
                + " need the ITF `#map` encoding");
      }
      return (Map<Object, Object>) Delegation.deserialize(delegate, node, p, ctxt);
    }
    Map<Object, Object> map = newMap(p);
    for (JsonNode pair : node.get(ItfNodes.MAP)) {
      if (!pair.isArray() || pair.size() != 2) {
        throw JsonMappingException.from(p, "Expected `#map` entries to be [key, value] pairs");
      }
      Object key = ctxt.readTreeAsValue(pair.get(0), type.getKeyType());
      Object value = ctxt.readTreeAsValue(pair.get(1), type.getContentType());
      map.put(key, value);
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  private Map<Object, Object> newMap(JsonParser p) throws JsonMappingException {
    Class<?> raw = type.getRawClass();
    if (raw.isInterface() || Modifier.isAbstract(raw.getModifiers())) {
      if (SortedMap.class.isAssignableFrom(raw) || NavigableMap.class.isAssignableFrom(raw)) {
        return new TreeMap<>();
      }
      if (ConcurrentMap.class.isAssignableFrom(raw)) {
        return new ConcurrentHashMap<>();
      }
      return new LinkedHashMap<>();
    }
    try {
      Constructor<?> ctor = raw.getDeclaredConstructor();
      ctor.setAccessible(true);
      return (Map<Object, Object>) ctor.newInstance();
    } catch (NoSuchMethodException
        | InstantiationException
        | IllegalAccessException
        | InvocationTargetException e) {
      throw JsonMappingException.from(
          p, "Cannot instantiate " + raw.getName() + ": no accessible no-arg constructor");
    }
  }
}
