package io.openems.edge.meter.socomec.digiware.iac;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.INVERT_IF_TRUE;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.MeterType;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.element.SignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.co2e.scope2.Co2eScope2;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.statechannelruleengine.StateChannelRuleEngineProvider;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.socomec.digiware.AbstractSocomecIacMeter;
import io.openems.edge.meter.socomec.digiware.LoadSelection;
import io.openems.edge.meter.socomec.digiware.SocomecIacMeter;
import io.openems.edge.timeofusetariff.morocco.api.TimeOfUseTariffMorocco;

@Designate(ocd = Config.class, factory = true)
@Component(//
        name = "Meter.Socomec.Digiware.Iac", //
        immediate = true, //
        configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class MeterSocomecDigiwareIacImpl extends AbstractSocomecIacMeter
        implements MeterSocomecDigiwareIac, SocomecIacMeter, ElectricityMeter, ModbusComponent, OpenemsComponent,
        ModbusSlave, StateChannelRuleEngineProvider {

    private final Logger log = LoggerFactory.getLogger(MeterSocomecDigiwareIacImpl.class);

    @Reference
    private ConfigurationAdmin cm;

    @Override
    @Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
    protected void setModbus(BridgeModbus modbus) {
        super.setModbus(modbus);
    }

    @Reference
    private TimeOfUseTariffMorocco tariffComponent;

    @Reference
    private Co2eScope2 co2eScope2Component;

    private Config config;

    public MeterSocomecDigiwareIacImpl() {
        super(//
                OpenemsComponent.ChannelId.values(), //
                ModbusComponent.ChannelId.values(), //
                ElectricityMeter.ChannelId.values(), //
                SocomecIacMeter.ChannelId.values(), //
                MeterSocomecDigiwareIac.ChannelId.values() //
        );

        ElectricityMeter.calculateSumCurrentFromPhases(this);
        ElectricityMeter.calculateAverageVoltageFromPhases(this);
    }

    @Activate
    private void activate(ComponentContext context, Config config) throws OpenemsException {
        if (super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
                "Modbus", config.modbus_id())) {
            return;
        }
        this.config = config;
        this.identifySocomecMeter();

        ElectricityMeter.calculateEnergyCost(this, this.tariffComponent);
        ElectricityMeter.calculateEnergyEmissions(this, this.co2eScope2Component);
        StateChannelRuleEngineProvider.applyRules(this, config.stateChannelRules());
    }

    @Override
    @Deactivate
    protected void deactivate() {
        StateChannelRuleEngineProvider.applyRules(this, "[]");
        super.deactivate();
    }

    @Override
    public MeterType getMeterType() {
        return this.config.type();
    }

    @Override
    public io.openems.edge.common.channel.ChannelId[] getEligibleChannelIds() {
        return new io.openems.edge.common.channel.ChannelId[] {
                ElectricityMeter.ChannelId.ACTIVE_POWER,
                ElectricityMeter.ChannelId.REACTIVE_POWER,
                ElectricityMeter.ChannelId.APPARENT_POWER,
                ElectricityMeter.ChannelId.FREQUENCY,
                ElectricityMeter.ChannelId.VOLTAGE_L1,
                ElectricityMeter.ChannelId.VOLTAGE_L2,
                ElectricityMeter.ChannelId.VOLTAGE_L3,
                ElectricityMeter.ChannelId.CURRENT_L1,
                ElectricityMeter.ChannelId.CURRENT_L2,
                ElectricityMeter.ChannelId.CURRENT_L3
        };
    }

    @Override
    public io.openems.edge.common.channel.Channel<?> addDynamicChannel(
            io.openems.edge.common.channel.ChannelId channelId) {
        return this.addChannel(channelId);
    }

    @Override
    public void removeDynamicChannel(io.openems.edge.common.channel.Channel<?> channel) {
        this.removeChannel(channel);
    }
    @Override
    protected int getLoadBaseAddress() {
        return this.config.loadSelection().getBaseAddress();
    }

    // -------------------------------------------------------------------------
    // identifiedI60 — supports load tables 1-6
    // -------------------------------------------------------------------------

    @Override
    protected void identifiedI60() throws OpenemsException {
        this.applyLoadProtocol();
    }

    // -------------------------------------------------------------------------
    // identifiedI30 — supports load tables 1-3 only
    // -------------------------------------------------------------------------

    @Override
    protected void identifiedI30() throws OpenemsException {
        if (this.config.loadSelection().getValue() > 3) {
            this.logError(this.log, "Load " + this.config.loadSelection().getName()
                    + " is not available on Digiware I-30 (only loads 1-3 supported)");
            this.channel(SocomecIacMeter.ChannelId.UNKNOWN_LOAD_TABLE).setNextValue(true);
            return;
        }
        this.applyLoadProtocol();
    }

    // -------------------------------------------------------------------------
    // identifiedI35 — left empty intentionally (special case, handled later)
    // -------------------------------------------------------------------------

    @Override
    protected void identifiedI35() throws OpenemsException {
    		if (this.config.loadSelection().getValue() > 3) {
            this.logError(this.log, "Load " + this.config.loadSelection().getName()
                    + " is not available on Digiware I-35 (only loads 1-3 supported)");
            this.channel(SocomecIacMeter.ChannelId.UNKNOWN_LOAD_TABLE).setNextValue(true);
            return;
        }
        this.applyLoadProtocol();
    }

    // -------------------------------------------------------------------------
    // Shared protocol builder — used by I60 and I30 after validation
    // -------------------------------------------------------------------------

    private void applyLoadProtocol() throws OpenemsException {
        final var load = this.config.loadSelection();
        final int base = load.getBaseAddress();
        final int energyBase = load.getEnergyBaseAddress();
        final boolean invert = this.config.invert();

        // Step 1: instantaneous measurements — HIGH priority
        this.modbusProtocol.addTask(new FC3ReadRegistersTask(base + LoadSelection.Offset.FREQUENCY, Priority.HIGH, //
                m(ElectricityMeter.ChannelId.FREQUENCY,
                        new UnsignedDoublewordElement(base + LoadSelection.Offset.FREQUENCY),
                        SCALE_FACTOR_MINUS_3), // mHz → Hz (offsets 10-11)
                m(ElectricityMeter.ChannelId.VOLTAGE_L1,
                        new UnsignedDoublewordElement(base + LoadSelection.Offset.VOLTAGE_V1),
                        SCALE_FACTOR_MINUS_2), // V×10⁻² → V (offsets 12-13)
                m(ElectricityMeter.ChannelId.VOLTAGE_L2,
                        new UnsignedDoublewordElement(base + LoadSelection.Offset.VOLTAGE_V2),
                        SCALE_FACTOR_MINUS_2), // offsets 14-15
                m(ElectricityMeter.ChannelId.VOLTAGE_L3,
                        new UnsignedDoublewordElement(base + LoadSelection.Offset.VOLTAGE_V3),
                        SCALE_FACTOR_MINUS_2) // offsets 16-17
        ));

        this.modbusProtocol.addTask(new FC3ReadRegistersTask(base + LoadSelection.Offset.CURRENT_I1, Priority.HIGH, //
                m(ElectricityMeter.ChannelId.CURRENT_L1,
                        new UnsignedDoublewordElement(base + LoadSelection.Offset.CURRENT_I1),
                        SCALE_FACTOR_MINUS_3), // mA → A
                m(ElectricityMeter.ChannelId.CURRENT_L2,
                        new UnsignedDoublewordElement(base + LoadSelection.Offset.CURRENT_I2),
                        SCALE_FACTOR_MINUS_3), //
                m(ElectricityMeter.ChannelId.CURRENT_L3,
                        new UnsignedDoublewordElement(base + LoadSelection.Offset.CURRENT_I3),
                        SCALE_FACTOR_MINUS_3), //
                m(MeterSocomecDigiwareIac.ChannelId.CURRENT_IN,
                        new UnsignedDoublewordElement(base + LoadSelection.Offset.CURRENT_IN),
                        SCALE_FACTOR_MINUS_3) //
        ));

        this.modbusProtocol.addTask(new FC3ReadRegistersTask(base + LoadSelection.Offset.SNOM, Priority.HIGH, //
                m(MeterSocomecDigiwareIac.ChannelId.SNOM,
                        new UnsignedDoublewordElement(base + LoadSelection.Offset.SNOM)), //
                m(ElectricityMeter.ChannelId.ACTIVE_POWER,
                        new SignedDoublewordElement(base + LoadSelection.Offset.TOTAL_ACTIVE_POWER),
                        INVERT_IF_TRUE(invert)), //
                m(ElectricityMeter.ChannelId.REACTIVE_POWER,
                        new SignedDoublewordElement(base + LoadSelection.Offset.TOTAL_REACTIVE_POWER),
                        INVERT_IF_TRUE(invert)), //
                m(MeterSocomecDigiwareIac.ChannelId.TOTAL_LAGGING_REACTIVE_POWER,
                        new SignedDoublewordElement(base + LoadSelection.Offset.TOTAL_LAGGING_REACTIVE_POWER),
                        INVERT_IF_TRUE(invert)), //
                m(MeterSocomecDigiwareIac.ChannelId.TOTAL_LEADING_REACTIVE_POWER,
                        new SignedDoublewordElement(base + LoadSelection.Offset.TOTAL_LEADING_REACTIVE_POWER),
                        INVERT_IF_TRUE(invert)), //
                m(ElectricityMeter.ChannelId.APPARENT_POWER,
                        new UnsignedDoublewordElement(base + LoadSelection.Offset.TOTAL_APPARENT_POWER)), //
                m(MeterSocomecDigiwareIac.ChannelId.TOTAL_POWER_FACTOR,
                        new io.openems.edge.bridge.modbus.api.element.SignedWordElement(
                                base + LoadSelection.Offset.TOTAL_POWER_FACTOR)), // S16 at offset +54
                m(MeterSocomecDigiwareIac.ChannelId.POWER_FACTOR_TYPE,
                        new UnsignedWordElement(base + LoadSelection.Offset.POWER_FACTOR_TYPE)) // U8 at offset +55
        ));

        // Step 2: energy counters — LOW priority (change slowly)
        // Ea+ (positive active energy) → ACTIVE_CONSUMPTION_ENERGY (consumption-only meter)
        // Values are in kWh, OpenEMS expects Wh → SCALE_FACTOR_3
        this.modbusProtocol.addTask(new FC3ReadRegistersTask(
                energyBase + LoadSelection.EnergyOffset.TOTAL_POSITIVE_ACTIVE_ENERGY, Priority.LOW, //
                m(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY,
                        new UnsignedDoublewordElement(
                                energyBase + LoadSelection.EnergyOffset.TOTAL_POSITIVE_ACTIVE_ENERGY),
                        SCALE_FACTOR_3) // kWh → Wh
        ));
    }

    @Override
    public String debugLog() {
        return "L:" + this.config.loadSelection().getName() //
                + "|P:" + this.getActivePower().asString() //
                + "|E:" + this.getActiveConsumptionEnergy().asString();
    }

    @Override
    public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
        return new ModbusSlaveTable(//
                OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
                ElectricityMeter.getModbusSlaveNatureTable(accessMode) //
        );
    }
}
