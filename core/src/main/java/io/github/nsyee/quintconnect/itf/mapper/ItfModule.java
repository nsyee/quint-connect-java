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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.MapType;
import java.util.Optional;

/**
 * Jackson module implementing the Quint-specific mapping rules of {@link ItfMapper}.
 *
 * <p>Register it on your own {@code ObjectMapper} if you need to combine it with other modules;
 * {@link ItfMapper#ItfMapper()} does so automatically. The module only kicks in where Jackson has
 * no explicit instruction: a sealed interface annotated with {@code @JsonTypeInfo} keeps Jackson's
 * polymorphic handling, {@code @JsonProperty} renames are honoured, and so on.
 *
 * <p>Rules added:
 *
 * <ul>
 *   <li>sealed interfaces: {@code { tag, value }} → the permitted subtype whose simple name (or
 *       {@link ItfVariant}) equals {@code tag}; see {@link SealedTypeDeserializer};
 *   <li>enums: {@code { tag: "X", value: () }} → {@code X}, in addition to plain strings;
 *   <li>{@link Optional}: {@code Some(x)} / {@code None} → {@code Optional.of(x)} / {@code
 *       Optional.empty()};
 *   <li>maps: {@code {"#map": [[k, v], …]}} → {@code Map<K, V>} with keys deserialised as {@code
 *       K};
 *   <li>records: a tuple (JSON array) maps positionally onto the record components;
 *   <li>record components are required unless their type is {@link Optional}, like {@code serde}.
 * </ul>
 */
public final class ItfModule extends SimpleModule {

  private static final long serialVersionUID = 1L;

  /** Creates the module. */
  public ItfModule() {
    super("quint-connect-itf");
  }

  @Override
  public void setupModule(SetupContext context) {
    super.setupModule(context);
    context.addDeserializers(new ItfDeserializers());
    context.addBeanDeserializerModifier(new ItfDeserializerModifier());
    context.insertAnnotationIntrospector(new RequiredComponentsIntrospector());
  }

  /** Custom deserializers for types Jackson would otherwise treat as beans. */
  private static final class ItfDeserializers extends Deserializers.Base {

    @Override
    public JsonDeserializer<?> findBeanDeserializer(
        JavaType type, DeserializationConfig config, BeanDescription beanDesc)
        throws JsonMappingException {
      Class<?> raw = type.getRawClass();
      if (raw == Optional.class) {
        JavaType content = type.containedTypeOrUnknown(0);
        return new OptionalDeserializer(type, content);
      }
      if (Variants.isSealedRoot(raw)
          && !beanDesc.getClassInfo().hasAnnotation(JsonTypeInfo.class)) {
        return new SealedTypeDeserializer(type);
      }
      return null;
    }
  }

  /** Wraps Jackson's standard deserializers to accept the Quint encodings first. */
  private static final class ItfDeserializerModifier extends BeanDeserializerModifier {

    private static final long serialVersionUID = 1L;

    @Override
    public JsonDeserializer<?> modifyDeserializer(
        DeserializationConfig config, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
      Class<?> raw = beanDesc.getBeanClass();
      if (raw.isRecord()) {
        return new RecordTupleDeserializer(deserializer, raw);
      }
      return deserializer;
    }

    @Override
    public JsonDeserializer<?> modifyEnumDeserializer(
        DeserializationConfig config,
        JavaType type,
        BeanDescription beanDesc,
        JsonDeserializer<?> deserializer) {
      return new TaggedEnumDeserializer(deserializer);
    }

    @Override
    public JsonDeserializer<?> modifyMapDeserializer(
        DeserializationConfig config,
        MapType type,
        BeanDescription beanDesc,
        JsonDeserializer<?> deserializer) {
      return new ItfMapDeserializer(deserializer, type);
    }
  }

  /**
   * Marks every canonical-constructor parameter of a record as required, except {@link Optional}
   * ones, so that a missing field is an error instead of a silent {@code null}/{@code 0}.
   */
  private static final class RequiredComponentsIntrospector extends NopAnnotationIntrospector {

    private static final long serialVersionUID = 1L;

    @Override
    public Boolean hasRequiredMarker(AnnotatedMember member) {
      if (member instanceof AnnotatedParameter parameter
          && parameter.getDeclaringClass().isRecord()
          && parameter.getRawType() != Optional.class) {
        return Boolean.TRUE;
      }
      return null;
    }
  }
}
