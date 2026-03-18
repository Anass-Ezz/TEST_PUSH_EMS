package io.openems.edge.meter.socomec.digiware;

import static io.openems.edge.bridge.modbus.api.ModbusUtils.readElementOnce;
import static io.openems.edge.bridge.modbus.api.ModbusUtils.FunctionCode.FC3;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.ModbusUtils;
import io.openems.edge.bridge.modbus.api.element.StringWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedQuadruplewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.meter.api.ElectricityMeter;

public abstract class AbstractSocomecIacMeter extends AbstractOpenemsModbusComponent
        implements SocomecIacMeter, ElectricityMeter, OpenemsComponent, ModbusSlave {

    private final Logger log = LoggerFactory.getLogger(AbstractSocomecIacMeter.class);

    protected final ModbusProtocol modbusProtocol;

    protected AbstractSocomecIacMeter(io.openems.edge.common.channel.ChannelId[] firstInitialChannelIds,
            io.openems.edge.common.channel.ChannelId[]... furtherInitialChannelIds) {
        super(firstInitialChannelIds, furtherInitialChannelIds);
        this.modbusProtocol = new ModbusProtocol(this);
    }

    @Override
    protected final ModbusProtocol defineModbusProtocol() {
        return this.modbusProtocol;
    }

    /**
     * Applies the modbus protocol for Socomec Digiware I-60.
     * Supports load tables 1-6.
     *
     * @throws OpenemsException on error
     */
    protected abstract void identifiedI60() throws OpenemsException;

    /**
     * Applies the modbus protocol for Socomec Digiware I-30.
     * Supports load tables 1-3 only.
     *
     * @throws OpenemsException on error
     */
    protected abstract void identifiedI30() throws OpenemsException;

    /**
     * Applies the modbus protocol for Socomec Digiware I-35.
     * Supports load tables 1-3 only.
     *
     * @throws OpenemsException on error
     */
    protected abstract void identifiedI35() throws OpenemsException;

    /**
     * Returns the Modbus base address of the configured load table.
     * Used by the abstract class to perform the load status check after
     * identification.
     *
     * @return the load table base address
     */
    protected abstract int getLoadBaseAddress();

    protected final void identifySocomecMeter() {
        this.getSocomecIdentifier().thenAccept(name -> {
            try {

                if (name.contains("i-60")) {
                    this.logInfo(this.log, "Identified Socomec Digiware I-60 meter");
                    this.identifiedI60();
                    this.checkLoadStatus(this.getLoadBaseAddress());

                } else if (name.contains("i-35")) {
                    this.logInfo(this.log, "Identified Socomec Digiware I-35 meter");
                    this.identifiedI35();

                } else if (name.contains("i-30")) {
                    this.logInfo(this.log, "Identified Socomec Digiware I-30 meter");
                    this.identifiedI30();
                    this.checkLoadStatus(this.getLoadBaseAddress());

                } else {
                    this.logError(this.log, "Unable to identify Socomec Digiware [" + name + "] meter!");
                    this.channel(SocomecIacMeter.ChannelId.UNKNOWN_SOCOMEC_METER).setNextValue(true);
                }

            } catch (OpenemsException e) {
                this.channel(SocomecIacMeter.ChannelId.UNKNOWN_SOCOMEC_METER).setNextValue(true);
                this.logError(this.log,
                        "Error while trying to identify Socomec Digiware [" + name + "] meter: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Reads the Load Status register (offset 0 of the load base address) once and
     * raises {@link SocomecIacMeter.ChannelId#LOAD_TABLE_NOT_ENABLED} if disabled.
     *
     * @param loadBaseAddress the Modbus base address of the selected load table
     */
    private void checkLoadStatus(int loadBaseAddress) {
        readElementOnce(FC3, this.modbusProtocol, ModbusUtils::retryOnNull,
                new UnsignedWordElement(loadBaseAddress))
                .thenAccept(status -> {
                    if (status == null || status == 0) {
                        this.logError(this.log, "Load at base address 0x"
                                + Integer.toHexString(loadBaseAddress) + " is not enabled on the meter");
                        this.channel(SocomecIacMeter.ChannelId.LOAD_TABLE_NOT_ENABLED).setNextValue(true);
                    }
                });
    }

    /**
     * Gets the SOCOMEC identifier via Modbus.
     *
     * @return the future String; returns an empty string on error, never an exception
     */
    private CompletableFuture<String> getSocomecIdentifier() {
        final var result = new CompletableFuture<String>();

        readElementOnce(FC3, this.modbusProtocol, ModbusUtils::retryOnNull, new UnsignedQuadruplewordElement(0xC350))
                .thenAccept(value -> {
                    if (value != 0x0053004F0043004FL /* SOCO */) {
                        this.channel(SocomecIacMeter.ChannelId.NO_SOCOMEC_METER).setNextValue(true);
                        result.complete(String.valueOf(value));
                        return;
                    }
                    readElementOnce(FC3, this.modbusProtocol, ModbusUtils::retryOnNull,
                            new StringWordElement(0xC38A, 8))
                            .thenAccept(name -> result.complete(name.toLowerCase()));
                });

        return result;
    }
}
