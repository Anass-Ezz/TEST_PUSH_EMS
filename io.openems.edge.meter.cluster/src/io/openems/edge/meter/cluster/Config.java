package io.openems.edge.meter.cluster;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Meter Cluster", //
		description = "A virtual meter that aggregates multiple other meters.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "meterCluster0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Meter IDs", description = "List of Component-IDs of the Meters to cluster")
	String[] meter_ids() default {};

	String webconsole_configurationFactory_nameHint() default "Meter Cluster [{id}]";
}