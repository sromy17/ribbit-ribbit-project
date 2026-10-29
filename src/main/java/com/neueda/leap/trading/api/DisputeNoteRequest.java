package com.neueda.leap.trading.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DisputeNoteRequest(@NotBlank @Size(max = 1000) String note, @NotNull Long expectedVersion) { }