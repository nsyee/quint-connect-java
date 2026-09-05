/**
 * Mapping between {@link io.github.nsyee.quintconnect.itf.ItfValue} trees and user-defined Java
 * types, the Java replacement for {@code serde::Deserialize} in the Rust crate.
 *
 * <p>{@link io.github.nsyee.quintconnect.itf.mapper.ItfMapper} is the entry point. It is a thin
 * layer over a Jackson {@code ObjectMapper} configured with {@link
 * io.github.nsyee.quintconnect.itf.mapper.ItfModule}, which teaches Jackson the Quint conventions:
 * sum types as sealed interfaces or enums, {@code Option} as {@link java.util.Optional}, maps with
 * non-string keys, tuples as records and unbounded integers with overflow checks.
 */
package io.github.nsyee.quintconnect.itf.mapper;
