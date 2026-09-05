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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the Quint variant tag a permitted subtype of a sealed interface corresponds to.
 *
 * <p>By default a variant is matched by its simple class name: {@code record Occupied(Player p)}
 * handles {@code { tag: "Occupied", value: … }}. Use this annotation when the Java name must
 * differ, e.g. because the Quint tag is not a legal or idiomatic Java identifier:
 *
 * <pre>{@code
 * sealed interface Stage permits Working, Done {}
 * record Working() implements Stage {}
 * @ItfVariant("committed") record Done() implements Stage {}
 * }</pre>
 *
 * <p>A one-component variant is ambiguous in the reverse direction ({@link ItfMapper#toItf}): Quint
 * {@code Prepared("p1")} and {@code Prepared({ node: "p1" })} both map to {@code record
 * Prepared(String node)}. Deserialisation accepts either; set {@link #record()} so that the
 * component is written back as a one-field record instead of a bare value.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ItfVariant {
  /** The tag Quint uses for this variant; empty keeps the simple class name. */
  String value() default "";

  /**
   * Whether the payload of a one-component variant is a Quint record ({@code Tag({ field: v })})
   * rather than the bare value ({@code Tag(v)}). Irrelevant for 0 or 2+ components.
   */
  boolean record() default false;
}
