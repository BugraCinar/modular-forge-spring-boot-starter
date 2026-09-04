package dev.modularforge.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.rate-limit.fail-open=true")
@AutoConfigureMockMvc
@ActiveProfiles({ "integration", "prod" })
class SwaggerProdProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("prod profile does not expose OpenAPI docs")
    void prodProfile_doesNotExposeOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }
}
