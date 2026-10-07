package com.stockflow.common;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class FrontendBundleConsistencyTest {
    @Test void committedBundlesMatchFeatureSources() throws Exception {
        assertThat(Files.readString(Path.of("src/main/resources/static/index.html"))).contains("type=\"module\"");
        assertThat(Files.readString(Path.of("src/main/resources/static/app.js"))).contains("import(", "loadFragments");
        var manifest = new ObjectMapper().readTree(Files.readString(Path.of("frontend/manifest.json")));
        var fields = manifest.fields();
        while (fields.hasNext()) {
            var bundle = fields.next(); var expected = new StringBuilder();
            for (var fragment : bundle.getValue()) expected.append(Files.readString(Path.of("frontend", fragment.asText())).replace("\r\n", "\n"));
            assertThat(Files.readString(Path.of("src/main/resources/static", bundle.getKey())).replace("\r\n", "\n"))
                    .as("Run node scripts/build-storefront.cjs after editing frontend source")
                    .isEqualTo(expected.toString());
        }
    }
}
