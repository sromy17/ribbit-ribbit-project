package com.neueda.leap.trading.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DisputeTransitionRequest(
	@NotNull Long expectedVersion,
	@Size(max = 1000) String reason,
	@Size(max = 30) String outcome,
	Integer newAssigneeUserId
) { }