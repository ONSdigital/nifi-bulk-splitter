/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.gov.ons.ai.bulk;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.apache.commons.lang.StringUtils;
import org.apache.nifi.annotation.behavior.*;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.apache.nifi.flowfile.attributes.FragmentAttributes.*;

@EventDriven
@SideEffectFree
@SupportsBatching
@Tags({"json", "split", "jsonpath", "ONS"})
@InputRequirement(Requirement.INPUT_REQUIRED)
@CapabilityDescription("Splits a JSON Array into multiple, separate FlowFiles based on a batch size for an array element specified by a JsonPath expression. "
        + "Each generated FlowFile is comprised of an element of the specified array and transferred to relationship 'split,' "
        + "with the original file transferred to the 'original' relationship. If the specified JsonPath is not found or "
        + "does not evaluate to an array element, the original file is routed to 'failure' and no files are generated.")
@WritesAttributes({
        @WritesAttribute(attribute = "fragment.identifier",
                description = "All split FlowFiles produced from the same parent FlowFile will have the same randomly generated UUID added for this attribute"),
        @WritesAttribute(attribute = "fragment.index",
                description = "A one-up number that indicates the ordering of the split FlowFiles that were created from a single parent FlowFile"),
        @WritesAttribute(attribute = "fragment.count",
                description = "The number of split FlowFiles generated from the parent FlowFile"),
        @WritesAttribute(attribute = "segment.original.filename ", description = "The filename of the parent FlowFile")
})
@SystemResourceConsideration(resource = SystemResource.MEMORY, description = "The entirety of the FlowFile's content (as a JsonNode object) is read into memory, " +
        "in addition to all of the generated FlowFiles representing the split JSON. If many splits are generated due to the size of the JSON, or how the JSON is " +
        "configured to be split, a two-phase approach may be necessary to avoid excessive use of memory.")
public class JsonArrayBatchSplitter extends AbstractJsonPathProcessor {

    public static final PropertyDescriptor ARRAY_JSON_PATH_EXPRESSION = new PropertyDescriptor.Builder()
            .name("JsonPath Expression")
            .description("A JsonPath expression that indicates the array element to split into JSON/scalar fragments.")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR) // Full validation/caching occurs in #customValidate
            .required(true)
            .build();

    public static final PropertyDescriptor ARRAY_JSON_BATCH_SIZE = new PropertyDescriptor.Builder()
            .name("Batch Size")
            .description("The number of items in a batch.")
            .addValidator(StandardValidators.INTEGER_VALIDATOR)
            .defaultValue("50")
            .required(true)
            .build();

    public static final Relationship REL_ORIGINAL = new Relationship.Builder()
            .name("original")
            .description("The original FlowFile that was split into segments. If the FlowFile fails processing, nothing will be sent to "
                    + "this relationship")
            .build();
    public static final Relationship REL_SPLIT = new Relationship.Builder()
            .name("split")
            .description("All segments of the original FlowFile will be routed to this relationship")
            .build();

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("If a FlowFile fails processing for any reason (for example, the FlowFile is not valid JSON or the specified "
                    + "path does not exist), it will be routed to this relationship")
            .build();

    private List<PropertyDescriptor> properties;
    private Set<Relationship> relationships;

    private final AtomicReference<JsonPath> JSON_PATH_REF = new AtomicReference<>();
    private volatile String nullDefaultValue;
    final Logger log = LoggerFactory.getLogger(JsonArrayBatchSplitter.class);

    @Override
    protected void init(final ProcessorInitializationContext context) {
        super.init(context);
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(ARRAY_JSON_PATH_EXPRESSION);
        properties.add(ARRAY_JSON_BATCH_SIZE);
        properties.add(NULL_VALUE_DEFAULT_REPRESENTATION);
        this.properties = Collections.unmodifiableList(properties);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_ORIGINAL);
        relationships.add(REL_SPLIT);
        relationships.add(REL_FAILURE);
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    @Override
    public void onPropertyModified(PropertyDescriptor descriptor, String oldValue, String newValue) {
        if (descriptor.equals(ARRAY_JSON_PATH_EXPRESSION)) {
            if (!StringUtils.equals(oldValue, newValue)) {
                if (oldValue != null) {
                    // clear the cached item
                    JSON_PATH_REF.set(null);
                }
            }
        }
    }

    @Override
    protected Collection<ValidationResult> customValidate(ValidationContext validationContext) {
        JsonPathValidator validator = new JsonPathValidator() {
            @Override
            public void cacheComputedValue(String subject, String input, JsonPath computedJson) {
                JSON_PATH_REF.set(computedJson);
            }

            @Override
            public boolean isStale(String subject, String input) {
                return JSON_PATH_REF.get() == null;
            }
        };

        String value = validationContext.getProperty(ARRAY_JSON_PATH_EXPRESSION).getValue();
        return Collections.singleton(validator.validate(ARRAY_JSON_PATH_EXPRESSION.getName(), value, validationContext));
    }

    @OnScheduled
    public void onScheduled(ProcessContext processContext) {
        nullDefaultValue = NULL_REPRESENTATION_MAP.get(processContext.getProperty(NULL_VALUE_DEFAULT_REPRESENTATION).getValue());
    }

    @Override
    public void onTrigger(final ProcessContext processContext, final ProcessSession processSession) {
        FlowFile original = processSession.get();

        if (original == null) {
            return;
        }

        final ComponentLog logger = getLogger();
        int batchSize = processContext.getProperty(ARRAY_JSON_BATCH_SIZE).asInteger();

        DocumentContext documentContext;
        try {
            documentContext = validateAndEstablishJsonContext(processSession, original);
        } catch (InvalidJsonException e) {
            logger.error("FlowFile {} did not have valid JSON content.", new Object[]{original});
            processSession.transfer(original, REL_FAILURE);
            return;
        }

        final JsonPath jsonPath = JSON_PATH_REF.get();

        Object jsonPathResult;

        try {
            jsonPathResult = documentContext.read(jsonPath);
        } catch (PathNotFoundException e) {
            logger.warn("JsonPath {} could not be found for FlowFile {}", new Object[]{jsonPath.getPath(), original});
            processSession.transfer(original, REL_FAILURE);
            return;
        }

        if (!(jsonPathResult instanceof List)) {
            logger.error("The evaluated value {} of {} was not a JSON Array compatible type and cannot be split.",
                    new Object[]{jsonPathResult, jsonPath.getPath()});
            processSession.transfer(original, REL_FAILURE);
            return;
        }

        List resultList = (List) jsonPathResult;

        Collection<List> subSets = partition(resultList, batchSize);

        Map<String, String> attributes = new HashMap<>();
        final String fragmentId = UUID.randomUUID().toString();
        attributes.put(FRAGMENT_ID.key(), fragmentId);
        attributes.put(FRAGMENT_COUNT.key(), Integer.toString(subSets.size()));

        int index = 1;

        for (List fragment : subSets) {

            StringBuilder ss = new StringBuilder();

            FlowFile split = processSession.create(original);
            for (Object aFragment : fragment) {
                Map resultSeg = (Map) aFragment;
                // Data is (should be) a id, address pair in a map.
                JSONObject obj = new JSONObject();
                obj.put("id", resultSeg.get("id"));
                obj.put("address", resultSeg.get("address"));
                String resultSegment =obj.toJSONString();
                if (ss.toString().equals("")) {
                    ss = new StringBuilder(resultSegment);
                } else {
                    ss.append(",").append(resultSegment);
                }
            }

            ss = new StringBuilder("{\"addresses\":[" + ss + "]}");
            final String jsonArray = ss.toString();

            split = processSession.write(split, (out) -> {
                        String resultSegmentContent = getResultRepresentation(jsonArray, nullDefaultValue);
                        out.write(resultSegmentContent.getBytes(StandardCharsets.UTF_8));
                    }
            );
            attributes.put(SEGMENT_ORIGINAL_FILENAME.key(), split.getAttribute(CoreAttributes.FILENAME.key()));
            attributes.put(FRAGMENT_INDEX.key(), Integer.toString(index++));
            processSession.transfer(processSession.putAllAttributes(split, attributes), REL_SPLIT);
        }

        original = copyAttributesToOriginal(processSession, original, fragmentId, resultList.size());
        processSession.transfer(original, REL_ORIGINAL);
        logger.info("Split {} into {} FlowFiles", new Object[]{original, resultList.size()});
    }

    private <T> Collection<List<T>> partition(List<T> list, int size) {
        final AtomicInteger counter = new AtomicInteger(0);

        return list.stream()
                .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / size))
                .values();
    }
}
