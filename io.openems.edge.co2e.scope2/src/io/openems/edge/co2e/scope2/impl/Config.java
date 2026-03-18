package io.openems.edge.co2e.scope2.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "CO2e Scope 2", //
		description = "Provides the Scope 2 CO2 emission factor (kg CO2e / kWh) by polling Odoo via the OpenEMS Backend.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "co2eScope2-0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Polling Interval [Minutes]", description = "Interval in minutes to fetch the CO2e factor from the Odoo backend.")
	int pollingInterval() default 15;

	String webconsole_configurationFactory_nameHint() default "CO2e Scope 2 [{id}]";

}
