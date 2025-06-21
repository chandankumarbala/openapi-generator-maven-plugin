package com.openapispecs.generator.plugin.parser.mixin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// This mix-in tells Jackson to ignore the 'exampleSetFlag' property on the MediaType class during serialization.
@JsonIgnoreProperties({ "exampleSetFlag" })
public abstract class MediaTypeMixin {
}
