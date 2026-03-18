package io.openems.edge.co2e.scope2.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;
import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.jsonrpc.request.Co2eScope2Request;
import io.openems.common.jsonrpc.response.Co2eScope2Response;
import io.openems.common.utils.ThreadPoolUtils;
import io.openems.edge.co2e.scope2.Co2eScope2;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.user.User;
import io.openems.edge.controller.api.backend.api.ControllerApiBackend;
@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "io.openems.edge.co2e.scope2", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class Co2eScope2Impl extends AbstractOpenemsComponent implements Co2eScope2, OpenemsComponent {

	public record Timeout(long amount, TimeUnit unit) {
	}

	private static final Timeout DEFAULT_TIMEOUT = new Timeout(30, TimeUnit.SECONDS);
	private final Logger log = LoggerFactory.getLogger(this.getClass());

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	@Reference(//
			policy = ReferencePolicy.DYNAMIC, //
			policyOption = ReferencePolicyOption.GREEDY, //
			cardinality = ReferenceCardinality.OPTIONAL, //
			target = "(enabled=true)" //
	)
	private volatile ControllerApiBackend ctrlBackend;

	@Reference
	private ComponentManager componentManager;

	private int pollingIntervalMinutes = 15;

	public Co2eScope2Impl() {
		super(OpenemsComponent.ChannelId.values(), Co2eScope2.ChannelId.values());
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.pollingIntervalMinutes = config.pollingInterval();

		if (config.enabled()) {
			this.executor.schedule(this.task, 0, TimeUnit.MINUTES);
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		ThreadPoolUtils.shutdownAndAwaitTermination(this.executor, 0);
		super.deactivate();
	}

	private final Runnable task = () -> {
		try {
			var request = new Co2eScope2Request();
			var response = this.handleRequest(null, request, DEFAULT_TIMEOUT);
			var co2eResponse = Co2eScope2Response.from(response);

			this.channel(Co2eScope2.ChannelId.FACTOR).setNextValue(co2eResponse.getFactor());
			this.channel(Co2eScope2.ChannelId.COMMUNICATION_FAILED).setNextValue(false);

		} catch (OpenemsNamedException e) {
			this.log.error("Failed to query CO2e Scope 2 factor from backend: " + e.getMessage());
			this.channel(Co2eScope2.ChannelId.COMMUNICATION_FAILED).setNextValue(true);
		} catch (Exception e) {
			this.log.error("Unexpected error querying CO2e Scope 2 factor: " + e.getMessage());
			this.channel(Co2eScope2.ChannelId.COMMUNICATION_FAILED).setNextValue(true);
		}

		// Schedule next execution using the configured polling interval
		this.executor.schedule(this.task, this.pollingIntervalMinutes, TimeUnit.MINUTES);
	};

	private CompletableFuture<? extends JsonrpcResponseSuccess> handleRequestAsync(User user,
			JsonrpcRequest request, Timeout timeout) throws OpenemsNamedException {
		return this.getBackendOrError().sendRequest(user, request) //
				.orTimeout(timeout.amount(), timeout.unit());
	}

	private JsonrpcResponseSuccess handleRequest(User user, JsonrpcRequest request, Timeout timeout)
			throws OpenemsNamedException {
		try {
			return this.handleRequestAsync(user, request, timeout).get();
		} catch (InterruptedException | ExecutionException e) {
			throw getOpenemsException(e);
		}
	}

	private ControllerApiBackend getBackendOrError() throws OpenemsNamedException {
		final var backendApi = this.getBackend();
		if (backendApi == null || !backendApi.isConnected()) {
			throw new OpenemsException("Backend not connected!");
		}
		return backendApi;
	}

	private ControllerApiBackend getBackend() {
		if (this.ctrlBackend != null) {
			return this.ctrlBackend;
		}
		final var backendApis = this.componentManager.getEnabledComponentsOfType(ControllerApiBackend.class);
		if (backendApis.isEmpty()) {
			return null;
		}
		this.log.warn("ControllerApiBackend exists but was not injected!");
		return backendApis.get(0);
	}

	private static OpenemsNamedException getOpenemsException(Throwable e) {
		return getOpenemsException(e, true);
	}

	private static OpenemsNamedException getOpenemsException(Throwable e, boolean isRootException) {
		if (e instanceof OpenemsNamedException one) {
			return one;
		}
		if (e.getCause() != null) {
			final var foundOpenemsException = getOpenemsException(e.getCause(), false);
			if (foundOpenemsException != null) {
				return foundOpenemsException;
			}
		}
		if (!isRootException) {
			return null;
		}
		return new OpenemsException(e.getMessage());
	}

	@Override
	public Double getNowFactor() {
		return this.getFactor().get();
	}

}
