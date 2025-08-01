/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.common.ValidationException;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.core.Booleans;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.inference.InputType;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.core.ml.inference.assignment.AdaptiveAllocationsSettings;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.xpack.inference.Utils.modifiableMap;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.convertMapStringsToSecureString;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.convertToUri;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.createUri;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractOptionalEnum;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractOptionalList;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractOptionalListOfStringTuples;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractOptionalMap;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractOptionalPositiveInteger;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractOptionalPositiveLong;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractOptionalString;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractOptionalTimeValue;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractRequiredMap;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractRequiredPositiveInteger;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractRequiredPositiveIntegerLessThanOrEqualToMax;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractRequiredSecureString;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.extractRequiredString;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.removeNullValues;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.validateMapStringValues;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.validateMapValues;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class ServiceUtilsTests extends ESTestCase {
    public void testRemoveAsTypeWithTheCorrectType() {
        Map<String, Object> map = new HashMap<>(Map.of("a", 5, "b", "a string", "c", Boolean.TRUE, "d", 1.0));

        Integer i = ServiceUtils.removeAsType(map, "a", Integer.class);
        assertEquals(Integer.valueOf(5), i);
        assertNull(map.get("a")); // field has been removed

        String str = ServiceUtils.removeAsType(map, "b", String.class);
        assertEquals("a string", str);
        assertNull(map.get("b"));

        Boolean b = ServiceUtils.removeAsType(map, "c", Boolean.class);
        assertEquals(Boolean.TRUE, b);
        assertNull(map.get("c"));

        Double d = ServiceUtils.removeAsType(map, "d", Double.class);
        assertEquals(Double.valueOf(1.0), d);
        assertNull(map.get("d"));

        assertThat(map.entrySet(), empty());
    }

    public void testRemoveAsType_Validation_WithTheCorrectType() {
        Map<String, Object> map = new HashMap<>(Map.of("a", 5, "b", "a string", "c", Boolean.TRUE, "d", 1.0));

        ValidationException validationException = new ValidationException();
        Integer i = ServiceUtils.removeAsType(map, "a", Integer.class, validationException);
        assertEquals(Integer.valueOf(5), i);
        assertNull(map.get("a")); // field has been removed
        assertThat(validationException.validationErrors(), empty());

        String str = ServiceUtils.removeAsType(map, "b", String.class, validationException);
        assertEquals("a string", str);
        assertNull(map.get("b"));
        assertThat(validationException.validationErrors(), empty());
    }

    public void testRemoveAsTypeWithInCorrectType() {
        Map<String, Object> map = new HashMap<>(Map.of("a", 5, "b", "a string", "c", Boolean.TRUE, "d", 5.0, "e", 5));

        var e = expectThrows(ElasticsearchStatusException.class, () -> ServiceUtils.removeAsType(map, "a", String.class));
        assertThat(
            e.getMessage(),
            containsString("field [a] is not of the expected type. The value [5] cannot be converted to a [String]")
        );
        assertNull(map.get("a"));

        e = expectThrows(ElasticsearchStatusException.class, () -> ServiceUtils.removeAsType(map, "b", Boolean.class));
        assertThat(
            e.getMessage(),
            containsString("field [b] is not of the expected type. The value [a string] cannot be converted to a [Boolean]")
        );
        assertNull(map.get("b"));

        e = expectThrows(ElasticsearchStatusException.class, () -> ServiceUtils.removeAsType(map, "c", Integer.class));
        assertThat(
            e.getMessage(),
            containsString("field [c] is not of the expected type. The value [true] cannot be converted to a [Integer]")
        );
        assertNull(map.get("c"));

        // cannot convert double to integer
        e = expectThrows(ElasticsearchStatusException.class, () -> ServiceUtils.removeAsType(map, "d", Integer.class));
        assertThat(
            e.getMessage(),
            containsString("field [d] is not of the expected type. The value [5.0] cannot be converted to a [Integer]")
        );
        assertNull(map.get("d"));

        // cannot convert integer to double
        e = expectThrows(ElasticsearchStatusException.class, () -> ServiceUtils.removeAsType(map, "e", Double.class));
        assertThat(
            e.getMessage(),
            containsString("field [e] is not of the expected type. The value [5] cannot be converted to a [Double]")
        );
        assertNull(map.get("e"));

        assertThat(map.entrySet(), empty());
    }

    public void testRemoveAsType_Validation_WithInCorrectType() {
        Map<String, Object> map = new HashMap<>(Map.of("a", 5, "b", "a string", "c", Boolean.TRUE, "d", 5.0, "e", 5));

        var validationException = new ValidationException();
        Object result = ServiceUtils.removeAsType(map, "a", String.class, validationException);
        assertNull(result);
        assertThat(validationException.validationErrors(), hasSize(1));
        assertThat(
            validationException.validationErrors().get(0),
            containsString("field [a] is not of the expected type. The value [5] cannot be converted to a [String]")
        );
        assertNull(map.get("a"));

        validationException = new ValidationException();
        ServiceUtils.removeAsType(map, "b", Boolean.class, validationException);
        assertThat(validationException.validationErrors(), hasSize(1));
        assertThat(
            validationException.validationErrors().get(0),
            containsString("field [b] is not of the expected type. The value [a string] cannot be converted to a [Boolean]")
        );
        assertNull(map.get("b"));

        validationException = new ValidationException();
        result = ServiceUtils.removeAsType(map, "c", Integer.class, validationException);
        assertNull(result);
        assertThat(validationException.validationErrors(), hasSize(1));
        assertThat(
            validationException.validationErrors().get(0),
            containsString("field [c] is not of the expected type. The value [true] cannot be converted to a [Integer]")
        );
        assertNull(map.get("c"));

        // cannot convert double to integer
        validationException = new ValidationException();
        result = ServiceUtils.removeAsType(map, "d", Integer.class, validationException);
        assertNull(result);
        assertThat(validationException.validationErrors(), hasSize(1));
        assertThat(
            validationException.validationErrors().get(0),
            containsString("field [d] is not of the expected type. The value [5.0] cannot be converted to a [Integer]")
        );
        assertNull(map.get("d"));

        // cannot convert integer to double
        validationException = new ValidationException();
        result = ServiceUtils.removeAsType(map, "e", Double.class, validationException);
        assertNull(result);
        assertThat(validationException.validationErrors(), hasSize(1));
        assertThat(
            validationException.validationErrors().get(0),
            containsString("field [e] is not of the expected type. The value [5] cannot be converted to a [Double]")
        );
        assertNull(map.get("e"));

        assertThat(map.entrySet(), empty());
    }

    public void testRemoveAsTypeMissingReturnsNull() {
        Map<String, Object> map = new HashMap<>(Map.of("a", 5, "b", "a string", "c", Boolean.TRUE));
        assertNull(ServiceUtils.removeAsType(map, "missing", Integer.class));
        assertThat(map.entrySet(), hasSize(3));
    }

    public void testRemoveAsOneOfTypes_Validation_WithCorrectTypes() {
        Map<String, Object> map = new HashMap<>(Map.of("a", 5, "b", "a string", "c", Boolean.TRUE, "d", 1.0));
        ValidationException validationException = new ValidationException();

        Integer i = (Integer) ServiceUtils.removeAsOneOfTypes(map, "a", List.of(String.class, Integer.class), validationException);
        assertEquals(Integer.valueOf(5), i);
        assertNull(map.get("a")); // field has been removed

        String str = (String) ServiceUtils.removeAsOneOfTypes(map, "b", List.of(Integer.class, String.class), validationException);
        assertEquals("a string", str);
        assertNull(map.get("b"));

        Boolean b = (Boolean) ServiceUtils.removeAsOneOfTypes(map, "c", List.of(String.class, Boolean.class), validationException);
        assertEquals(Boolean.TRUE, b);
        assertNull(map.get("c"));

        Double d = (Double) ServiceUtils.removeAsOneOfTypes(map, "d", List.of(Booleans.class, Double.class), validationException);
        assertEquals(Double.valueOf(1.0), d);
        assertNull(map.get("d"));

        assertThat(map.entrySet(), empty());
    }

    public void testRemoveAsOneOfTypes_Validation_WithIncorrectType() {
        Map<String, Object> map = new HashMap<>(Map.of("a", 5, "b", "a string", "c", Boolean.TRUE, "d", 5.0, "e", 5));

        var validationException = new ValidationException();
        Object result = ServiceUtils.removeAsOneOfTypes(map, "a", List.of(String.class, Boolean.class), validationException);
        assertNull(result);
        assertThat(validationException.validationErrors(), hasSize(1));
        assertThat(
            validationException.validationErrors().get(0),
            containsString("field [a] is not of one of the expected types. The value [5] cannot be converted to one of [String, Boolean]")
        );
        assertNull(map.get("a"));

        validationException = new ValidationException();
        result = ServiceUtils.removeAsOneOfTypes(map, "b", List.of(Boolean.class, Integer.class), validationException);
        assertNull(result);
        assertThat(validationException.validationErrors(), hasSize(1));
        assertThat(
            validationException.validationErrors().get(0),
            containsString(
                "field [b] is not of one of the expected types. The value [a string] cannot be converted to one of [Boolean, Integer]"
            )
        );
        assertNull(map.get("b"));

        validationException = new ValidationException();
        result = ServiceUtils.removeAsOneOfTypes(map, "c", List.of(String.class, Integer.class), validationException);
        assertNull(result);
        assertThat(validationException.validationErrors(), hasSize(1));
        assertThat(
            validationException.validationErrors().get(0),
            containsString(
                "field [c] is not of one of the expected types. The value [true] cannot be converted to one of [String, Integer]"
            )
        );
        assertNull(map.get("c"));

        validationException = new ValidationException();
        result = ServiceUtils.removeAsOneOfTypes(map, "d", List.of(String.class, Boolean.class), validationException);
        assertNull(result);
        assertThat(validationException.validationErrors(), hasSize(1));
        assertThat(
            validationException.validationErrors().get(0),
            containsString("field [d] is not of one of the expected types. The value [5.0] cannot be converted to one of [String, Boolean]")
        );
        assertNull(map.get("d"));

        validationException = new ValidationException();
        result = ServiceUtils.removeAsOneOfTypes(map, "e", List.of(String.class, Boolean.class), validationException);
        assertNull(result);
        assertThat(validationException.validationErrors(), hasSize(1));
        assertThat(
            validationException.validationErrors().get(0),
            containsString("field [e] is not of one of the expected types. The value [5] cannot be converted to one of [String, Boolean]")
        );
        assertNull(map.get("e"));

        assertThat(map.entrySet(), empty());
    }

    public void testRemoveAsOneOfTypesMissingReturnsNull() {
        Map<String, Object> map = new HashMap<>(Map.of("a", 5, "b", "a string", "c", Boolean.TRUE));
        assertNull(ServiceUtils.removeAsOneOfTypes(map, "missing", List.of(Integer.class), new ValidationException()));
        assertThat(map.entrySet(), hasSize(3));
    }

    public void testRemoveAsAdaptiveAllocationsSettings() {
        Map<String, Object> map = new HashMap<>(
            Map.of("settings", new HashMap<>(Map.of("enabled", true, "min_number_of_allocations", 7, "max_number_of_allocations", 42)))
        );
        ValidationException validationException = new ValidationException();
        assertThat(
            ServiceUtils.removeAsAdaptiveAllocationsSettings(map, "settings", validationException),
            equalTo(new AdaptiveAllocationsSettings(true, 7, 42))
        );
        assertThat(validationException.validationErrors(), empty());

        assertThat(ServiceUtils.removeAsAdaptiveAllocationsSettings(map, "non-existent-key", validationException), nullValue());
        assertThat(validationException.validationErrors(), empty());

        map = new HashMap<>(Map.of("settings", new HashMap<>(Map.of("enabled", false))));
        assertThat(
            ServiceUtils.removeAsAdaptiveAllocationsSettings(map, "settings", validationException),
            equalTo(new AdaptiveAllocationsSettings(false, null, null))
        );
        assertThat(validationException.validationErrors(), empty());
    }

    public void testRemoveAsAdaptiveAllocationsSettings_exceptions() {
        Map<String, Object> map = new HashMap<>(
            Map.of("settings", new HashMap<>(Map.of("enabled", "YES!", "blah", 42, "max_number_of_allocations", -7)))
        );
        ValidationException validationException = new ValidationException();
        ServiceUtils.removeAsAdaptiveAllocationsSettings(map, "settings", validationException);
        assertThat(validationException.validationErrors(), hasSize(3));
        assertThat(
            validationException.validationErrors().get(0),
            containsString("field [enabled] is not of the expected type. The value [YES!] cannot be converted to a [Boolean]")
        );
        assertThat(validationException.validationErrors().get(1), containsString("[settings] does not allow the setting [blah]"));
        assertThat(
            validationException.validationErrors().get(2),
            containsString("[max_number_of_allocations] must be a positive integer or null")
        );
    }

    public void testConvertToUri_CreatesUri() {
        var validation = new ValidationException();
        var uri = convertToUri("www.elastic.co", "name", "scope", validation);

        assertNotNull(uri);
        assertTrue(validation.validationErrors().isEmpty());
        assertThat(uri.toString(), is("www.elastic.co"));
    }

    public void testConvertToUri_DoesNotThrowNullPointerException_WhenPassedNull() {
        var validation = new ValidationException();
        var uri = convertToUri(null, "name", "scope", validation);

        assertNull(uri);
        assertTrue(validation.validationErrors().isEmpty());
    }

    public void testConvertToUri_AddsValidationError_WhenUrlIsInvalid() {
        var validation = new ValidationException();
        var uri = convertToUri("^^", "name", "scope", validation);

        assertNull(uri);
        assertThat(validation.validationErrors().size(), is(1));
        assertThat(validation.validationErrors().get(0), containsString("[scope] Invalid url [^^] received for field [name]"));
    }

    public void testConvertToUri_AddsValidationError_WhenUrlIsInvalid_PreservesReason() {
        var validation = new ValidationException();
        var uri = convertToUri("^^", "name", "scope", validation);

        assertNull(uri);
        assertThat(validation.validationErrors().size(), is(1));
        assertThat(
            validation.validationErrors().get(0),
            is("[scope] Invalid url [^^] received for field [name]. Error: unable to parse url [^^]. Reason: Illegal character in path")
        );
    }

    public void testCreateUri_CreatesUri() {
        var uri = createUri("www.elastic.co");

        assertNotNull(uri);
        assertThat(uri.toString(), is("www.elastic.co"));
    }

    public void testCreateUri_ThrowsException_WithInvalidUrl() {
        var exception = expectThrows(IllegalArgumentException.class, () -> createUri("^^"));

        assertThat(exception.getMessage(), containsString("unable to parse url [^^]"));
    }

    public void testCreateUri_ThrowsException_WithNullUrl() {
        expectThrows(NullPointerException.class, () -> createUri(null));
    }

    public void testExtractRequiredSecureString_CreatesSecureString() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", "value"));
        var secureString = extractRequiredSecureString(map, "key", "scope", validation);

        assertTrue(validation.validationErrors().isEmpty());
        assertNotNull(secureString);
        assertThat(secureString.toString(), is("value"));
        assertTrue(map.isEmpty());
    }

    public void testExtractRequiredSecureString_AddsException_WhenFieldDoesNotExist() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", "value"));
        var secureString = extractRequiredSecureString(map, "abc", "scope", validation);

        assertNull(secureString);
        assertFalse(validation.validationErrors().isEmpty());
        assertThat(map.size(), is(1));
        assertThat(validation.validationErrors().get(0), is("[scope] does not contain the required setting [abc]"));
    }

    public void testExtractRequiredSecureString_AddsException_WhenFieldIsEmpty() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", ""));
        var createdString = extractOptionalString(map, "key", "scope", validation);

        assertNull(createdString);
        assertFalse(validation.validationErrors().isEmpty());
        assertTrue(map.isEmpty());
        assertThat(validation.validationErrors().get(0), is("[scope] Invalid value empty string. [key] must be a non-empty string"));
    }

    public void testExtractRequiredString_CreatesString() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", "value"));
        var createdString = extractRequiredString(map, "key", "scope", validation);

        assertThat(validation.validationErrors(), hasSize(1));
        assertNotNull(createdString);
        assertThat(createdString, is("value"));
        assertTrue(map.isEmpty());
    }

    public void testExtractRequiredString_AddsException_WhenFieldDoesNotExist() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");

        Map<String, Object> map = modifiableMap(Map.of("key", "value"));
        var createdString = extractRequiredSecureString(map, "abc", "scope", validation);

        assertNull(createdString);
        assertThat(validation.validationErrors(), hasSize(2));
        assertThat(map.size(), is(1));
        assertThat(validation.validationErrors().get(1), is("[scope] does not contain the required setting [abc]"));
    }

    public void testExtractRequiredString_AddsException_WhenFieldIsEmpty() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", ""));
        var createdString = extractOptionalString(map, "key", "scope", validation);

        assertNull(createdString);
        assertFalse(validation.validationErrors().isEmpty());
        assertTrue(map.isEmpty());
        assertThat(validation.validationErrors().get(1), is("[scope] Invalid value empty string. [key] must be a non-empty string"));
    }

    public void testExtractOptionalString_CreatesString() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", "value"));
        var createdString = extractOptionalString(map, "key", "scope", validation);

        assertTrue(validation.validationErrors().isEmpty());
        assertNotNull(createdString);
        assertThat(createdString, is("value"));
        assertTrue(map.isEmpty());
    }

    public void testExtractOptionalString_DoesNotAddException_WhenFieldDoesNotExist() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", "value"));
        var createdString = extractOptionalString(map, "abc", "scope", validation);

        assertNull(createdString);
        assertThat(validation.validationErrors(), hasSize(1));
        assertThat(map.size(), is(1));
    }

    public void testExtractOptionalString_AddsException_WhenFieldIsEmpty() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", ""));
        var createdString = extractOptionalString(map, "key", "scope", validation);

        assertNull(createdString);
        assertFalse(validation.validationErrors().isEmpty());
        assertTrue(map.isEmpty());
        assertThat(validation.validationErrors().get(0), is("[scope] Invalid value empty string. [key] must be a non-empty string"));
    }

    public void testExtractOptionalList_CreatesList() {
        var validation = new ValidationException();
        var list = List.of(randomAlphaOfLength(10), randomAlphaOfLength(10));

        Map<String, Object> map = modifiableMap(Map.of("key", list));
        assertEquals(list, extractOptionalList(map, "key", String.class, validation));
        assertTrue(validation.validationErrors().isEmpty());
        assertTrue(map.isEmpty());
    }

    public void testExtractOptionalList_AddsException_WhenFieldDoesNotExist() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", List.of(randomAlphaOfLength(10), randomAlphaOfLength(10))));
        assertNull(extractOptionalList(map, "abc", String.class, validation));
        assertThat(validation.validationErrors(), hasSize(1));
        assertThat(map.size(), is(1));
    }

    public void testExtractOptionalList_AddsException_WhenFieldIsEmpty() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", ""));
        assertNull(extractOptionalList(map, "key", String.class, validation));
        assertFalse(validation.validationErrors().isEmpty());
        assertTrue(map.isEmpty());
    }

    public void testExtractOptionalList_AddsException_WhenFieldIsNotAList() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", 1));
        assertNull(extractOptionalList(map, "key", String.class, validation));
        assertFalse(validation.validationErrors().isEmpty());
        assertTrue(map.isEmpty());
    }

    public void testExtractOptionalList_AddsException_WhenFieldIsNotAListOfTheCorrectType() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", List.of(1, 2)));
        assertNull(extractOptionalList(map, "key", String.class, validation));
        assertFalse(validation.validationErrors().isEmpty());
        assertTrue(map.isEmpty());
    }

    public void testExtractOptionalList_AddsException_WhenFieldContainsMixedTypeValues() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", List.of(1, "a")));
        assertNull(extractOptionalList(map, "key", String.class, validation));
        assertFalse(validation.validationErrors().isEmpty());
        assertTrue(map.isEmpty());
    }

    public void testExtractOptionalPositiveInt() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("abc", 1));
        assertEquals(Integer.valueOf(1), extractOptionalPositiveInteger(map, "abc", "scope", validation));
        assertThat(validation.validationErrors(), hasSize(1));
    }

    public void testExtractOptionalPositiveLong_IntegerValue() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("abc", 3));
        assertEquals(Long.valueOf(3), extractOptionalPositiveLong(map, "abc", "scope", validation));
        assertThat(validation.validationErrors(), hasSize(1));
    }

    public void testExtractOptionalPositiveLong() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("abc", 4_000_000_000L));
        assertEquals(Long.valueOf(4_000_000_000L), extractOptionalPositiveLong(map, "abc", "scope", validation));
        assertThat(validation.validationErrors(), hasSize(1));
    }

    public void testExtractRequiredPositiveInteger_ReturnsValue() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", 1));
        var parsedInt = extractRequiredPositiveInteger(map, "key", "scope", validation);

        assertThat(validation.validationErrors(), hasSize(1));
        assertNotNull(parsedInt);
        assertThat(parsedInt, is(1));
        assertTrue(map.isEmpty());
    }

    public void testExtractRequiredPositiveInteger_AddsErrorForNegativeValue() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", -1));
        var parsedInt = extractRequiredPositiveInteger(map, "key", "scope", validation);

        assertThat(validation.validationErrors(), hasSize(2));
        assertNull(parsedInt);
        assertTrue(map.isEmpty());
        assertThat(validation.validationErrors().get(1), is("[scope] Invalid value [-1]. [key] must be a positive integer"));
    }

    public void testExtractRequiredPositiveInteger_AddsErrorWhenKeyIsMissing() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", -1));
        var parsedInt = extractRequiredPositiveInteger(map, "not_key", "scope", validation);

        assertThat(validation.validationErrors(), hasSize(2));
        assertNull(parsedInt);
        assertThat(validation.validationErrors().get(1), is("[scope] does not contain the required setting [not_key]"));
    }

    public void testExtractRequiredPositiveIntegerLessThanOrEqualToMax_ReturnsValueWhenValueIsLessThanMax() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", 1));
        var parsedInt = extractRequiredPositiveIntegerLessThanOrEqualToMax(map, "key", 5, "scope", validation);

        assertThat(validation.validationErrors(), hasSize(1));
        assertNotNull(parsedInt);
        assertThat(parsedInt, is(1));
        assertTrue(map.isEmpty());
    }

    public void testExtractRequiredPositiveIntegerLessThanOrEqualToMax_ReturnsValueWhenValueIsEqualToMax() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", 5));
        var parsedInt = extractRequiredPositiveIntegerLessThanOrEqualToMax(map, "key", 5, "scope", validation);

        assertThat(validation.validationErrors(), hasSize(1));
        assertNotNull(parsedInt);
        assertThat(parsedInt, is(5));
        assertTrue(map.isEmpty());
    }

    public void testExtractRequiredPositiveIntegerLessThanOrEqualToMax_AddsErrorForNegativeValue() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", -1));
        var parsedInt = extractRequiredPositiveIntegerLessThanOrEqualToMax(map, "key", 5, "scope", validation);

        assertThat(validation.validationErrors(), hasSize(2));
        assertNull(parsedInt);
        assertTrue(map.isEmpty());
        assertThat(validation.validationErrors().get(1), is("[scope] Invalid value [-1]. [key] must be a positive integer"));
    }

    public void testExtractRequiredPositiveIntegerLessThanOrEqualToMax_AddsErrorWhenKeyIsMissing() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", -1));
        var parsedInt = extractRequiredPositiveIntegerLessThanOrEqualToMax(map, "not_key", 5, "scope", validation);

        assertThat(validation.validationErrors(), hasSize(2));
        assertNull(parsedInt);
        assertThat(validation.validationErrors().get(1), is("[scope] does not contain the required setting [not_key]"));
    }

    public void testExtractRequiredPositiveIntegerLessThanOrEqualToMax_AddsErrorWhenValueIsGreaterThanMax() {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", 6));
        var parsedInt = extractRequiredPositiveIntegerLessThanOrEqualToMax(map, "not_key", 5, "scope", validation);

        assertThat(validation.validationErrors(), hasSize(2));
        assertNull(parsedInt);
        assertThat(validation.validationErrors().get(1), is("[scope] does not contain the required setting [not_key]"));
    }

    public void testExtractRequiredPositiveIntegerBetween_ReturnsValueWhenValueIsBetweenMinAndMax() {
        var minValue = randomNonNegativeInt();
        var maxValue = randomIntBetween(minValue + 2, minValue + 10);
        testExtractRequiredPositiveIntegerBetween_Successful(minValue, maxValue, randomIntBetween(minValue + 1, maxValue - 1));
    }

    public void testExtractRequiredPositiveIntegerBetween_ReturnsValueWhenValueIsEqualToMin() {
        var minValue = randomNonNegativeInt();
        var maxValue = randomIntBetween(minValue + 1, minValue + 10);
        testExtractRequiredPositiveIntegerBetween_Successful(minValue, maxValue, minValue);
    }

    public void testExtractRequiredPositiveIntegerBetween_ReturnsValueWhenValueIsEqualToMax() {
        var minValue = randomNonNegativeInt();
        var maxValue = randomIntBetween(minValue + 1, minValue + 10);
        testExtractRequiredPositiveIntegerBetween_Successful(minValue, maxValue, maxValue);
    }

    private void testExtractRequiredPositiveIntegerBetween_Successful(int minValue, int maxValue, int actualValue) {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", actualValue));
        var parsedInt = ServiceUtils.extractRequiredPositiveIntegerBetween(map, "key", minValue, maxValue, "scope", validation);

        assertThat(validation.validationErrors(), hasSize(1));
        assertNotNull(parsedInt);
        assertThat(parsedInt, is(actualValue));
        assertTrue(map.isEmpty());
    }

    public void testExtractRequiredIntBetween_AddsErrorForValueBelowMin() {
        var minValue = randomNonNegativeInt();
        var maxValue = randomIntBetween(minValue, minValue + 10);
        testExtractRequiredIntBetween_Unsuccessful(minValue, maxValue, minValue - 1);
    }

    public void testExtractRequiredIntBetween_AddsErrorForValueAboveMax() {
        var minValue = randomNonNegativeInt();
        var maxValue = randomIntBetween(minValue, minValue + 10);
        testExtractRequiredIntBetween_Unsuccessful(minValue, maxValue, maxValue + 1);
    }

    private void testExtractRequiredIntBetween_Unsuccessful(int minValue, int maxValue, int actualValue) {
        var validation = new ValidationException();
        validation.addValidationError("previous error");
        Map<String, Object> map = modifiableMap(Map.of("key", actualValue));
        var parsedInt = ServiceUtils.extractRequiredPositiveIntegerBetween(map, "key", minValue, maxValue, "scope", validation);

        assertThat(validation.validationErrors(), hasSize(2));
        assertNull(parsedInt);
        assertTrue(map.isEmpty());
        assertThat(validation.validationErrors().get(1), containsString("Invalid value"));
    }

    public void testExtractOptionalEnum_ReturnsNull_WhenFieldDoesNotExist() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", "value"));
        var createdEnum = extractOptionalEnum(map, "abc", "scope", InputType::fromString, EnumSet.allOf(InputType.class), validation);

        assertNull(createdEnum);
        assertTrue(validation.validationErrors().isEmpty());
        assertThat(map.size(), is(1));
    }

    public void testExtractOptionalEnum_ReturnsNullAndAddsException_WhenAnInvalidValueExists() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", "invalid_value"));
        var createdEnum = extractOptionalEnum(
            map,
            "key",
            "scope",
            InputType::fromString,
            EnumSet.of(InputType.INGEST, InputType.SEARCH),
            validation
        );

        assertNull(createdEnum);
        assertFalse(validation.validationErrors().isEmpty());
        assertTrue(map.isEmpty());
        assertThat(
            validation.validationErrors().get(0),
            is("[scope] Invalid value [invalid_value] received. [key] must be one of [ingest, search]")
        );
    }

    public void testExtractOptionalEnum_ReturnsNullAndAddsException_WhenValueIsNotPartOfTheAcceptableValues() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", InputType.UNSPECIFIED.toString()));
        var createdEnum = extractOptionalEnum(map, "key", "scope", InputType::fromString, EnumSet.of(InputType.INGEST), validation);

        assertNull(createdEnum);
        assertFalse(validation.validationErrors().isEmpty());
        assertTrue(map.isEmpty());
        assertThat(validation.validationErrors().get(0), is("[scope] Invalid value [unspecified] received. [key] must be one of [ingest]"));
    }

    public void testExtractOptionalEnum_ReturnsIngest_WhenValueIsAcceptable() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", InputType.INGEST.toString()));
        var createdEnum = extractOptionalEnum(map, "key", "scope", InputType::fromString, EnumSet.of(InputType.INGEST), validation);

        assertThat(createdEnum, is(InputType.INGEST));
        assertTrue(validation.validationErrors().isEmpty());
        assertTrue(map.isEmpty());
    }

    public void testExtractOptionalEnum_ReturnsClassification_WhenValueIsAcceptable() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", InputType.CLASSIFICATION.toString()));
        var createdEnum = extractOptionalEnum(
            map,
            "key",
            "scope",
            InputType::fromString,
            EnumSet.of(InputType.INGEST, InputType.CLASSIFICATION),
            validation
        );

        assertThat(createdEnum, is(InputType.CLASSIFICATION));
        assertTrue(validation.validationErrors().isEmpty());
        assertTrue(map.isEmpty());
    }

    public void testExtractOptionalTimeValue_ReturnsNull_WhenKeyDoesNotExist() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", 1));
        var timeValue = extractOptionalTimeValue(map, "a", "scope", validation);

        assertNull(timeValue);
        assertTrue(validation.validationErrors().isEmpty());
    }

    public void testExtractOptionalTimeValue_CreatesTimeValue_Of3Seconds() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", "3s"));
        var timeValue = extractOptionalTimeValue(map, "key", "scope", validation);

        assertTrue(validation.validationErrors().isEmpty());
        assertNotNull(timeValue);
        assertThat(timeValue, is(TimeValue.timeValueSeconds(3)));
        assertTrue(map.isEmpty());
    }

    public void testExtractOptionalTimeValue_ReturnsNullAndAddsException_WhenTimeValueIsInvalid_InvalidUnit() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", "3abc"));
        var timeValue = extractOptionalTimeValue(map, "key", "scope", validation);

        assertFalse(validation.validationErrors().isEmpty());
        assertNull(timeValue);
        assertTrue(map.isEmpty());
        assertThat(
            validation.validationErrors().get(0),
            is(
                "[scope] Invalid time value [3abc]. [key] must be a valid time value string: failed to parse setting [key] "
                    + "with value [3abc] as a time value: unit is missing or unrecognized"
            )
        );
    }

    public void testExtractOptionalTimeValue_ReturnsNullAndAddsException_WhenTimeValueIsInvalid_NegativeNumber() {
        var validation = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", "-3d"));
        var timeValue = extractOptionalTimeValue(map, "key", "scope", validation);

        assertFalse(validation.validationErrors().isEmpty());
        assertNull(timeValue);
        assertTrue(map.isEmpty());
        assertThat(
            validation.validationErrors().get(0),
            is(
                "[scope] Invalid time value [-3d]. [key] must be a valid time value string: failed to parse setting [key] "
                    + "with value [-3d] as a time value: negative durations are not supported"
            )
        );
    }

    public void testExtractOptionalDouble_ExtractsAsDoubleInRange() {
        var validationException = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", 1.01));
        var result = ServiceUtils.extractOptionalDoubleInRange(map, "key", 0.0, 2.0, "test_scope", validationException);
        assertEquals(Double.valueOf(1.01), result);
        assertTrue(map.isEmpty());
        assertThat(validationException.validationErrors().size(), is(0));
    }

    public void testExtractOptionalDouble_InRange_ReturnsNullWhenKeyNotPresent() {
        var validationException = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", 1.01));
        var result = ServiceUtils.extractOptionalDoubleInRange(map, "other_key", 0.0, 2.0, "test_scope", validationException);
        assertNull(result);
        assertThat(map.size(), is(1));
        assertThat(map.get("key"), is(1.01));
    }

    public void testExtractOptionalDouble_InRange_HasErrorWhenBelowMinValue() {
        var validationException = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", -2.0));
        var result = ServiceUtils.extractOptionalDoubleInRange(map, "key", 0.0, 2.0, "test_scope", validationException);
        assertNull(result);
        assertThat(validationException.validationErrors().size(), is(1));
        assertThat(
            validationException.validationErrors().get(0),
            is("[test_scope] Invalid value [-2.0]. [key] must be a greater than or equal to [0.0]")
        );
    }

    public void testExtractOptionalDouble_InRange_HasErrorWhenAboveMaxValue() {
        var validationException = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", 12.0));
        var result = ServiceUtils.extractOptionalDoubleInRange(map, "key", 0.0, 2.0, "test_scope", validationException);
        assertNull(result);
        assertThat(validationException.validationErrors().size(), is(1));
        assertThat(
            validationException.validationErrors().get(0),
            is("[test_scope] Invalid value [12.0]. [key] must be a less than or equal to [2.0]")
        );
    }

    public void testExtractOptionalDouble_InRange_DoesNotCheckMinWhenNull() {
        var validationException = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", -2.0));
        var result = ServiceUtils.extractOptionalDoubleInRange(map, "key", null, 2.0, "test_scope", validationException);
        assertEquals(Double.valueOf(-2.0), result);
        assertTrue(map.isEmpty());
        assertThat(validationException.validationErrors().size(), is(0));
    }

    public void testExtractOptionalDouble_InRange_DoesNotCheckMaxWhenNull() {
        var validationException = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", 12.0));
        var result = ServiceUtils.extractOptionalDoubleInRange(map, "key", 0.0, null, "test_scope", validationException);
        assertEquals(Double.valueOf(12.0), result);
        assertTrue(map.isEmpty());
        assertThat(validationException.validationErrors().size(), is(0));
    }

    public void testExtractOptionalFloat_ExtractsAFloat() {
        Map<String, Object> map = modifiableMap(Map.of("key", 1.0f));
        var result = ServiceUtils.extractOptionalFloat(map, "key");
        assertThat(result, is(1.0f));
        assertTrue(map.isEmpty());
    }

    public void testExtractOptionalFloat_ReturnsNullWhenKeyNotPresent() {
        Map<String, Object> map = modifiableMap(Map.of("key", 1.0f));
        var result = ServiceUtils.extractOptionalFloat(map, "other_key");
        assertNull(result);
        assertThat(map.size(), is(1));
        assertThat(map.get("key"), is(1.0f));
    }

    public void testExtractRequiredEnum_ExtractsAEnum() {
        ValidationException validationException = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", "ingest"));
        var result = ServiceUtils.extractRequiredEnum(
            map,
            "key",
            "testscope",
            InputType::fromString,
            EnumSet.allOf(InputType.class),
            validationException
        );
        assertThat(result, is(InputType.INGEST));
    }

    public void testExtractRequiredEnum_ReturnsNullWhenEnumValueIsNotPresent() {
        ValidationException validationException = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", "invalid"));
        var result = ServiceUtils.extractRequiredEnum(
            map,
            "key",
            "testscope",
            InputType::fromString,
            EnumSet.allOf(InputType.class),
            validationException
        );
        assertNull(result);
        assertThat(validationException.validationErrors().size(), is(1));
        assertThat(validationException.validationErrors().get(0), containsString("Invalid value [invalid] received. [key] must be one of"));
    }

    public void testExtractRequiredEnum_HasValidationErrorOnMissingSetting() {
        ValidationException validationException = new ValidationException();
        Map<String, Object> map = modifiableMap(Map.of("key", "ingest"));
        var result = ServiceUtils.extractRequiredEnum(
            map,
            "missing_key",
            "testscope",
            InputType::fromString,
            EnumSet.allOf(InputType.class),
            validationException
        );
        assertNull(result);
        assertThat(validationException.validationErrors().size(), is(1));
        assertThat(validationException.validationErrors().get(0), is("[testscope] does not contain the required setting [missing_key]"));
    }

    public void testValidateInputType_NoValidationErrorsWhenInternalType() {
        ValidationException validationException = new ValidationException();

        ServiceUtils.validateInputTypeIsUnspecifiedOrInternal(InputType.INTERNAL_SEARCH, validationException);
        assertThat(validationException.validationErrors().size(), is(0));

        ServiceUtils.validateInputTypeIsUnspecifiedOrInternal(InputType.INTERNAL_INGEST, validationException);
        assertThat(validationException.validationErrors().size(), is(0));
    }

    public void testValidateInputType_NoValidationErrorsWhenInputTypeIsNullOrUnspecified() {
        ValidationException validationException = new ValidationException();

        ServiceUtils.validateInputTypeIsUnspecifiedOrInternal(InputType.UNSPECIFIED, validationException);
        assertThat(validationException.validationErrors().size(), is(0));

        ServiceUtils.validateInputTypeIsUnspecifiedOrInternal(null, validationException);
        assertThat(validationException.validationErrors().size(), is(0));
    }

    public void testValidateInputType_ValidationErrorsWhenInputTypeIsSpecified() {
        ValidationException validationException = new ValidationException();

        ServiceUtils.validateInputTypeIsUnspecifiedOrInternal(InputType.SEARCH, validationException);
        assertThat(validationException.validationErrors().size(), is(1));

        ServiceUtils.validateInputTypeIsUnspecifiedOrInternal(InputType.INGEST, validationException);
        assertThat(validationException.validationErrors().size(), is(2));

        ServiceUtils.validateInputTypeIsUnspecifiedOrInternal(InputType.CLASSIFICATION, validationException);
        assertThat(validationException.validationErrors().size(), is(3));

        ServiceUtils.validateInputTypeIsUnspecifiedOrInternal(InputType.CLUSTERING, validationException);
        assertThat(validationException.validationErrors().size(), is(4));
    }

    public void testExtractRequiredMap() {
        var validation = new ValidationException();
        var extractedMap = extractRequiredMap(modifiableMap(Map.of("setting", Map.of("key", "value"))), "setting", "scope", validation);

        assertTrue(validation.validationErrors().isEmpty());
        assertThat(extractedMap, is(Map.of("key", "value")));
    }

    public void testExtractRequiredMap_ReturnsNull_WhenTypeIsInvalid() {
        var validation = new ValidationException();
        var extractedMap = extractRequiredMap(modifiableMap(Map.of("setting", 123)), "setting", "scope", validation);

        assertNull(extractedMap);
        assertThat(
            validation.getMessage(),
            is("Validation Failed: 1: field [setting] is not of the expected type. The value [123] cannot be converted to a [Map];")
        );
    }

    public void testExtractRequiredMap_ReturnsNull_WhenMissingSetting() {
        var validation = new ValidationException();
        var extractedMap = extractRequiredMap(modifiableMap(Map.of("not_setting", Map.of("key", "value"))), "setting", "scope", validation);

        assertNull(extractedMap);
        assertThat(validation.getMessage(), is("Validation Failed: 1: [scope] does not contain the required setting [setting];"));
    }

    public void testExtractRequiredMap_ReturnsNull_WhenMapIsEmpty() {
        var validation = new ValidationException();
        var extractedMap = extractRequiredMap(modifiableMap(Map.of("setting", Map.of())), "setting", "scope", validation);

        assertNull(extractedMap);
        assertThat(
            validation.getMessage(),
            is("Validation Failed: 1: [scope] Invalid value empty map. [setting] must be a non-empty map;")
        );
    }

    public void testExtractOptionalMap() {
        var validation = new ValidationException();
        var extractedMap = extractOptionalMap(modifiableMap(Map.of("setting", Map.of("key", "value"))), "setting", "scope", validation);

        assertTrue(validation.validationErrors().isEmpty());
        assertThat(extractedMap, is(Map.of("key", "value")));
    }

    public void testExtractOptionalMap_ReturnsNull_WhenTypeIsInvalid() {
        var validation = new ValidationException();
        var extractedMap = extractOptionalMap(modifiableMap(Map.of("setting", 123)), "setting", "scope", validation);

        assertNull(extractedMap);
        assertThat(
            validation.getMessage(),
            is("Validation Failed: 1: field [setting] is not of the expected type. The value [123] cannot be converted to a [Map];")
        );
    }

    public void testExtractOptionalMap_ReturnsNull_WhenMissingSetting() {
        var validation = new ValidationException();
        var extractedMap = extractOptionalMap(modifiableMap(Map.of("not_setting", Map.of("key", "value"))), "setting", "scope", validation);

        assertNull(extractedMap);
        assertTrue(validation.validationErrors().isEmpty());
    }

    public void testExtractOptionalMap_ReturnsEmptyMap_WhenEmpty() {
        var validation = new ValidationException();
        var extractedMap = extractOptionalMap(modifiableMap(Map.of("setting", Map.of())), "setting", "scope", validation);

        assertThat(extractedMap, is(Map.of()));
    }

    public void testValidateMapValues() {
        var validation = new ValidationException();
        validateMapValues(
            Map.of("string_key", "abc", "num_key", Integer.valueOf(1)),
            List.of(String.class, Integer.class),
            "setting",
            validation,
            false
        );
    }

    public void testValidateMapValues_IgnoresNullMap() {
        var validation = new ValidationException();
        validateMapValues(null, List.of(String.class, Integer.class), "setting", validation, false);
    }

    public void testValidateMapValues_ThrowsException_WhenMapContainsInvalidTypes() {
        // Includes the invalid key and value in the exception message
        {
            var validation = new ValidationException();
            var exception = expectThrows(
                ValidationException.class,
                () -> validateMapValues(
                    Map.of("string_key", "abc", "num_key", Integer.valueOf(1)),
                    List.of(String.class),
                    "setting",
                    validation,
                    false
                )
            );

            assertThat(
                exception.getMessage(),
                is(
                    "Validation Failed: 1: Map field [setting] has an entry that is not valid, "
                        + "[num_key => 1]. Value type of [1] is not one of [String].;"
                )
            );
        }

        // Does not include the invalid key and value in the exception message
        {
            var validation = new ValidationException();
            var exception = expectThrows(
                ValidationException.class,
                () -> validateMapValues(
                    Map.of("string_key", "abc", "num_key", Integer.valueOf(1)),
                    List.of(String.class, List.class),
                    "setting",
                    validation,
                    true
                )
            );

            assertThat(
                exception.getMessage(),
                is(
                    "Validation Failed: 1: Map field [setting] has an entry that is not valid. "
                        + "Value type is not one of [List, String].;"
                )
            );
        }
    }

    public void testValidateMapStringValues() {
        var validation = new ValidationException();
        assertThat(
            validateMapStringValues(Map.of("string_key", "abc", "string_key2", new String("awesome")), "setting", validation, false),
            is(Map.of("string_key", "abc", "string_key2", "awesome"))
        );
    }

    public void testValidateMapStringValues_ReturnsEmptyMap_WhenMapIsNull() {
        var validation = new ValidationException();
        assertThat(validateMapStringValues(null, "setting", validation, false), is(Map.of()));
    }

    public void testValidateMapStringValues_ThrowsException_WhenMapContainsInvalidTypes() {
        // Includes the invalid key and value in the exception message
        {
            var validation = new ValidationException();
            var exception = expectThrows(
                ValidationException.class,
                () -> validateMapStringValues(Map.of("string_key", "abc", "num_key", Integer.valueOf(1)), "setting", validation, false)
            );

            assertThat(
                exception.getMessage(),
                is(
                    "Validation Failed: 1: Map field [setting] has an entry that is not valid, "
                        + "[num_key => 1]. Value type of [1] is not one of [String].;"
                )
            );
        }

        // Does not include the invalid key and value in the exception message
        {
            var validation = new ValidationException();
            var exception = expectThrows(
                ValidationException.class,
                () -> validateMapStringValues(Map.of("string_key", "abc", "num_key", Integer.valueOf(1)), "setting", validation, true)
            );

            assertThat(
                exception.getMessage(),
                is("Validation Failed: 1: Map field [setting] has an entry that is not valid. Value type is not one of [String].;")
            );
        }
    }

    public void testConvertMapStringsToSecureString() {
        var validation = new ValidationException();
        assertThat(
            convertMapStringsToSecureString(Map.of("key", "value", "key2", "abc"), "setting", validation),
            is(Map.of("key", new SecureString("value".toCharArray()), "key2", new SecureString("abc".toCharArray())))
        );
    }

    public void testConvertMapStringsToSecureString_ReturnsAnEmptyMap_WhenMapIsNull() {
        var validation = new ValidationException();
        assertThat(convertMapStringsToSecureString(null, "setting", validation), is(Map.of()));
    }

    public void testConvertMapStringsToSecureString_ThrowsException_WhenMapContainsInvalidTypes() {
        var validation = new ValidationException();
        var exception = expectThrows(
            ValidationException.class,
            () -> convertMapStringsToSecureString(Map.of("key", "value", "key2", 123), "setting", validation)
        );

        assertThat(
            exception.getMessage(),
            is("Validation Failed: 1: Map field [setting] has an entry that is not valid. Value type is not one of [String].;")
        );
    }

    public void testRemoveNullValues() {
        var map = new HashMap<String, Object>();
        map.put("key1", null);
        map.put("key2", "awesome");
        map.put("key3", null);

        assertThat(removeNullValues(map), is(Map.of("key2", "awesome")));
    }

    public void testRemoveNullValues_ReturnsNull_WhenMapIsNull() {
        assertNull(removeNullValues(null));
    }

    public void testExtractOptionalListOfStringTuples() {
        var validation = new ValidationException();
        assertThat(
            extractOptionalListOfStringTuples(
                modifiableMap(Map.of("params", List.of(List.of("key", "value"), List.of("key2", "value2")))),
                "params",
                "scope",
                validation
            ),
            is(List.of(new Tuple<>("key", "value"), new Tuple<>("key2", "value2")))
        );
    }

    public void testExtractOptionalListOfStringTuples_ReturnsNull_WhenFieldIsNotAList() {
        var validation = new ValidationException();
        assertNull(extractOptionalListOfStringTuples(modifiableMap(Map.of("params", Map.of())), "params", "scope", validation));

        assertThat(
            validation.getMessage(),
            is("Validation Failed: 1: field [params] is not of the expected type. The value [{}] cannot be converted to a [List];")
        );
    }

    public void testExtractOptionalListOfStringTuples_Exception_WhenTupleIsNotAList() {
        var validation = new ValidationException();
        var exception = expectThrows(
            ValidationException.class,
            () -> extractOptionalListOfStringTuples(modifiableMap(Map.of("params", List.of("string"))), "params", "scope", validation)
        );

        assertThat(
            exception.getMessage(),
            is(
                "Validation Failed: 1: [scope] failed to parse tuple list entry [0] for setting "
                    + "[params], expected a list but the entry is [String];"
            )
        );
    }

    public void testExtractOptionalListOfStringTuples_Exception_WhenTupleIsListSize2() {
        var validation = new ValidationException();
        var exception = expectThrows(
            ValidationException.class,
            () -> extractOptionalListOfStringTuples(
                modifiableMap(Map.of("params", List.of(List.of("string")))),
                "params",
                "scope",
                validation
            )
        );

        assertThat(
            exception.getMessage(),
            is(
                "Validation Failed: 1: [scope] failed to parse tuple list entry "
                    + "[0] for setting [params], the tuple list size must be two, but was [1];"
            )
        );
    }

    public void testExtractOptionalListOfStringTuples_Exception_WhenTupleFirstElement_IsNotAString() {
        var validation = new ValidationException();
        var exception = expectThrows(
            ValidationException.class,
            () -> extractOptionalListOfStringTuples(
                modifiableMap(Map.of("params", List.of(List.of(1, "value")))),
                "params",
                "scope",
                validation
            )
        );

        assertThat(
            exception.getMessage(),
            is(
                "Validation Failed: 1: [scope] failed to parse tuple list entry [0] for setting [params], "
                    + "the first element must be a string but was [Integer];"
            )
        );
    }

    public void testExtractOptionalListOfStringTuples_Exception_WhenTupleSecondElement_IsNotAString() {
        var validation = new ValidationException();
        var exception = expectThrows(
            ValidationException.class,
            () -> extractOptionalListOfStringTuples(
                modifiableMap(Map.of("params", List.of(List.of("key", 2)))),
                "params",
                "scope",
                validation
            )
        );

        assertThat(
            exception.getMessage(),
            is(
                "Validation Failed: 1: [scope] failed to parse tuple list entry [0] for setting [params], "
                    + "the second element must be a string but was [Integer];"
            )
        );
    }
}
