package io.openems.backend.common.jsonrpc.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.base.GenericJsonrpcRequest;
import io.openems.common.utils.JsonUtils;

public class AggregateAndQueryEdgesChannelsHistoryRequestTest {

	@Test
	public void testFrom() throws OpenemsNamedException {
		var requestString = "{\n" //
				+ "  \"jsonrpc\": \"2.0\",\n" //
				+ "  \"id\": \"1dcc3992-fa55-449d-a442-9ee64fd0fcbb\",\n" //
				+ "  \"method\": \"aggregateAndQueryEdgesChannelsHistory\",\n" //
				+ "  \"params\": {\n" //
				+ "    \"timezone\": \"Africa/Casablanca\",\n" //
				+ "    \"fromDate\": \"2026-03-01\",\n" //
				+ "    \"toDate\": \"2026-03-05\",\n" //
				+ "    \"resolution\": {\n" //
				+ "      \"value\": 15,\n" //
				+ "      \"unit\": \"MINUTES\"\n" //
				+ "    },\n" //
				+ "    \"aggregations\": [\n" //
				+ "      {\n" //
				+ "        \"key\": \"globalConsumptionEnergy\",\n" //
				+ "        \"channels\": [\n" //
				+ "          \"edge0/meter0/ActiveConsumptionEnergy\",\n" //
				+ "          \"edge1/meter0/ActiveConsumptionEnergy\"\n" //
				+ "        ],\n" //
				+ "        \"channelType\": \"energy\",\n" //
				+ "        \"channelsAggregationType\": \"sum\"\n" //
				+ "      }\n" //
				+ "    ]\n" //
				+ "  }\n" //
				+ "}";
		var genericRequest = GenericJsonrpcRequest.from(JsonUtils.parseToJsonObject(requestString));
		var request = AggregateAndQueryEdgesChannelsHistoryRequest.from(genericRequest);
		
		assertEquals("Africa/Casablanca", request.getTimezone().getId());
		assertEquals("2026-03-01T00:00Z[Africa/Casablanca]", request.getFromDate().toString());
		// toDate should be +1 day from the parsed value
		assertEquals("2026-03-06T00:00Z[Africa/Casablanca]", request.getToDate().toString());
		assertTrue(request.getResolution().isPresent());
		assertEquals(15, request.getResolution().get().getValue());
		assertEquals(1, request.getAggregations().size());
		
		var agg = request.getAggregations().get(0);
		assertEquals("globalConsumptionEnergy", agg.key());
		assertEquals("energy", agg.channelType().name().toLowerCase());
		assertEquals("sum", agg.channelsAggregationType().name().toLowerCase());
		assertEquals(2, agg.channels().size());
	}
}
