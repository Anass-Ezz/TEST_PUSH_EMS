package io.openems.edge.meter.cluster;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.types.MeterType;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.AbstractMetersCluster;
import io.openems.edge.meter.api.ClusteredMeter;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.timeofusetariff.morocco.api.TimeOfUseTariffMorocco;
import io.openems.edge.co2e.scope2.Co2eScope2;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.Cluster", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				EventConstants.EVENT_TOPIC + "=" + io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE //
		})
public class ClusteredMeterDeviceImpl extends AbstractMetersCluster
		implements ClusteredMeterDevice, ClusteredMeter, ElectricityMeter, OpenemsComponent, EventHandler {

	@Reference
	private ComponentManager componentManager;

	@Reference
	private TimeOfUseTariffMorocco tariffComponent;

	@Reference
	private Co2eScope2 co2eScope2Component;

	public ClusteredMeterDeviceImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				ClusteredMeter.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled(), config.meter_ids(),
				this.componentManager);
		
		// Calculate energy cost and emissions based on aggregated consumption
		ElectricityMeter.calculateEnergyCost(this, this.tariffComponent);
		ElectricityMeter.calculateEnergyEmissions(this, this.co2eScope2Component);
	}

	@Modified
	private void modified(ComponentContext context, Config config) {
		super.modified(context, config.id(), config.alias(), config.enabled(), config.meter_ids(),
				this.componentManager);
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public MeterType getMeterType() {
		return MeterType.CONSUMPTION_METERED; // Defaulting to PRODUCTION, could be configurable
	}
	
	@Override
	public String debugLog() {
		return "CO2:" + this.getActiveConsumptionEnergyEmissions().asString() 
			+ " C:" + this.getActiveConsumptionEnergyCost().asString() 
			+ " E:" + this.getActiveConsumptionEnergy().asString() 
			+ " P:" + this.getActivePower().asString();
	}
}
