package io.openems.edge.timeofusetariff.morocco;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "TimeOfUseTariff Morocco", //
		description = "")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "timeOfUseTariffMorocco0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Polling Interval [Minutes]", description = "Interval in minutes to fetch tariff data from Odoo backend.")
	int pollingInterval() default 15;

	String webconsole_configurationFactory_nameHint() default "TimeOfUseTariff Morocco [{id}]";

}