package com.openapispecs.generator.plugin.parser.mixin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// This mix-in tells Jackson to ignore the specified properties on the Schema class during serialization.
@JsonIgnoreProperties({ "exampleSetFlag", "types" })
public abstract class SchemaMixin {
}
