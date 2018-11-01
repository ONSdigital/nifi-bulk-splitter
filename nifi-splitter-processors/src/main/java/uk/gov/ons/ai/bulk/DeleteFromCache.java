package uk.gov.ons.ai.bulk;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.behavior.*;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.distributed.cache.client.DistributedMapCacheClient;
import org.apache.nifi.distributed.cache.client.Serializer;
import org.apache.nifi.expression.AttributeExpression;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.util.StandardValidators;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@EventDriven
@SideEffectFree
@Tags({"cache", "delete",  "ONS"})
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
@CapabilityDescription("Remove an entry from the distributed cache.")
public class DeleteFromCache extends AbstractProcessor {

    public static final PropertyDescriptor PROP_DISTRIBUTED_CACHE_SERVICE = new PropertyDescriptor.Builder()
            .name("Distributed Cache Service")
            .description("The Controller Service that is used to get the cached values.")
            .required(true)
            .identifiesControllerService(DistributedMapCacheClient.class)
            .build();

    public static final PropertyDescriptor PROP_CACHE_ENTRY_IDENTIFIER = new PropertyDescriptor.Builder()
            .name("Cache Entry Identifier")
            .description("A FlowFile attribute, or the results of an Attribute Expression Language statement, which will be evaluated "
                    + "against a FlowFile in order to determine the item to delete from the cache")
            .required(true)
            .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(AttributeExpression.ResultType.STRING, true))
            //.defaultValue('${hash.value}')
            //.expressionLanguageSupported(true)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("If the cache item was successfully deleted it will be routed to this relationship")
            .build();

    public static final Relationship REL_NOT_FOUND = new Relationship.Builder()
            .name("not-found")
            .description("If a FlowFile's Cache Entry Identifier was not found in the cache, it will be routed to this relationship")
            .build();

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("If unable to communicate with the cache or if the cache entry is evaluated to be blank, the FlowFile will be penalised and routed to this relationship")
            .build();

    private List<PropertyDescriptor> properties;
    private Set<Relationship> relationships;

    private final Serializer<String> keySerializer = (value, output) -> output.write(value.getBytes(StandardCharsets.UTF_8));

    @Override
    protected void init(final ProcessorInitializationContext context) {
        super.init(context);
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(PROP_CACHE_ENTRY_IDENTIFIER);
        properties.add(PROP_DISTRIBUTED_CACHE_SERVICE);
        this.properties = Collections.unmodifiableList(properties);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_FAILURE);
        relationships.add(REL_NOT_FOUND);
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
    public void onTrigger(final ProcessContext context, final ProcessSession session) {

        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        final ComponentLog logger = getLogger();

        final String cacheKey = context.getProperty(PROP_CACHE_ENTRY_IDENTIFIER).evaluateAttributeExpressions(flowFile).getValue();

        if (StringUtils.isBlank(cacheKey)) {
            logger.error("FlowFile {} has no attribute for given Cache Entry Identifier", new Object[]{flowFile});
            session.transfer(flowFile, REL_NOT_FOUND);
            return;
        }

        final DistributedMapCacheClient cache =
                context.getProperty(PROP_DISTRIBUTED_CACHE_SERVICE).asControllerService(DistributedMapCacheClient.class);
        try {
            cache.remove(cacheKey, keySerializer);
            session.transfer(flowFile, REL_SUCCESS);
        } catch (final IOException e) {
            flowFile = session.penalize(flowFile);
            session.transfer(flowFile, REL_FAILURE);
            logger.error("Unable to communicate with cache when processing {} due to {}", new Object[]{flowFile, e});
        }
    }


}
