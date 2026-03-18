package io.openems.edge.timeofusetariff.morocco;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;
import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.utils.ThreadPoolUtils;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.user.User;
import io.openems.edge.controller.api.backend.api.ControllerApiBackend;
import io.openems.common.jsonrpc.request.TimeOfUseTariffMoroccoRequest;
import io.openems.common.jsonrpc.response.TimeOfUseTariffMoroccoResponse;
import io.openems.edge.timeofusetariff.morocco.api.TimeOfUseTariffMorocco;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "io.openems.edge.timeofusetariff.morocco", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class TimeOfUseTariffMoroccoImpl extends AbstractOpenemsComponent implements TimeOfUseTariffMorocco, OpenemsComponent {

	public record Timeout(long amount, TimeUnit unit) {
	}

	private static final Timeout DEFAULT_TIMEOUT = new Timeout(30, TimeUnit.SECONDS);
	private final Logger log = LoggerFactory.getLogger(this.getClass());

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	@Reference( //
			policy = ReferencePolicy.DYNAMIC, //
			policyOption = ReferencePolicyOption.GREEDY, //
			cardinality = ReferenceCardinality.OPTIONAL, //
			target = "(enabled=true)" //
	)
	private volatile ControllerApiBackend ctrlBackend;

	@Reference
	private ComponentManager componentManager;

	private int pollingIntervalMinutes = 15;

	public TimeOfUseTariffMoroccoImpl() {
		super(OpenemsComponent.ChannelId.values(), TimeOfUseTariffMorocco.ChannelId.values());
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
			var request = new TimeOfUseTariffMoroccoRequest();
			var response = this.handleRequest(null, request, DEFAULT_TIMEOUT);
			var moroccoResponse = TimeOfUseTariffMoroccoResponse.from(response);

			this.getPricePeakChannel().setNextValue(moroccoResponse.getPricePeak());
			this.getPriceStandardChannel().setNextValue(moroccoResponse.getPriceStandard());
			this.getPriceOffPeakChannel().setNextValue(moroccoResponse.getPriceOffPeak());

		} catch (OpenemsNamedException e) {
			this.log.error("Failed to query TimeOfUseTariffMorocco prices from backend: " + e.getMessage());
		} catch (Exception e) {
			this.log.error("Unexpected error querying TimeOfUseTariffMorocco prices: " + e.getMessage());
		}

		// Schedule next execution using the configured polling interval
		this.executor.schedule(this.task, this.pollingIntervalMinutes, TimeUnit.MINUTES);
	};

	private final CompletableFuture<? extends JsonrpcResponseSuccess> handleRequestAsync(User user,
			JsonrpcRequest request, Timeout timeout) throws OpenemsNamedException {
		return this.getBackendOrError().sendRequest(user, request) //
				.orTimeout(timeout.amount(), timeout.unit());
	}

	private final JsonrpcResponseSuccess handleRequest(User user, JsonrpcRequest request, Timeout timeout)
			throws OpenemsNamedException {
		try {
			return this.handleRequestAsync(user, request, timeout).get();
		} catch (InterruptedException | ExecutionException e) {
			throw getOpenemsException(e);
		}
	}

	private final ControllerApiBackend getBackendOrError() throws OpenemsNamedException {
		final var backendApi = this.getBackend();
		if (backendApi == null || !backendApi.isConnected()) {
			throw new OpenemsException("Backend not connected!");
		}
		return backendApi;
	}

	private final ControllerApiBackend getBackend() {
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

	private static final OpenemsNamedException getOpenemsException(Throwable e) {
		return getOpenemsException(e, true);
	}

	private static final OpenemsNamedException getOpenemsException(Throwable e, boolean isRootException) {
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
	public Double getNowPrice() {

		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		int month = now.getMonthValue();
		int hour = now.getHour();

		Double peak = this.getPricePeakChannel().value().get();
		Double standard = this.getPriceStandardChannel().value().get();
		Double offPeak = this.getPriceOffPeakChannel().value().get();

		boolean isWinter = month >= 10 || month <= 3; // Oct 1 to Mar 31
		
		if (isWinter) {
			if (hour >= 17 && hour < 22) {
				return peak;
			} else if (hour >= 7 && hour < 17) {
				return standard;
			} else {
				return offPeak;
			}
		} else { // Summer (Apr 1 to Sep 30)
			if (hour >= 18 && hour < 23) {
				return peak;
			} else if (hour >= 7 && hour < 18) {
				return standard;
			} else {
				return offPeak;
			}
		}
	}

}
