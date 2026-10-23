package com.neueda.leap.trading.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisputeSubmissionRequest(
	@NotBlank String transactionType,
	@NotBlank String transactionId,
	@NotBlank @Size(max = 120) String title,
	@NotBlank @Size(max = 1000) String reason,
	@NotBlank @Size(max = 100) String idempotencyKey,
	boolean feeRelated
) { }
