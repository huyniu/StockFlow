package com.stockflow.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StorefrontMapIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired WarehouseRepository warehouses;
    @Autowired Flyway flyway;

    @Test
    void anonymousMapExposesThreeClearlyLabelledDemoPoints() throws Exception {
        JsonNode rows = readLocations();
        assertThat(rows).hasSize(3);
        for (JsonNode row : rows) {
            assertThat(row.path("demo").asBoolean()).isTrue();
            assertThat(row.path("name").asText()).startsWith("StockFlow");
            assertThat(row.path("address").asText()).startsWith("Khu vực");
            assertThat(row.path("latitude").asDouble()).isBetween(-90.0, 90.0);
            assertThat(row.path("longitude").asDouble()).isBetween(-180.0, 180.0);
            assertThat(row.has("warehouse_address")).isFalse();
            assertThat(row.size()).isBetween(7, 8);
        }
    }

    @Test
    void mapUsesRealActiveWarehouseIdWithoutAssumingIdsMatch() throws Exception {
        Warehouse active = warehouses.save(new Warehouse("MAP-" + UUID.randomUUID(),
                "Chi nhánh đang phục vụ", "Địa chỉ nội bộ", WarehouseStatus.ACTIVE));
        jdbc.update("UPDATE storefront_map_locations SET warehouse_code=? WHERE code='DEMO-HAN'", active.getCode());
        JsonNode point = readLocations().get(0);
        assertThat(point.path("warehouse_id").asLong()).isEqualTo(active.getId());
        assertThat(point.path("address").asText()).doesNotContain(active.getAddress());
    }

    @Test
    void missingOrInactiveWarehouseDoesNotBecomeASelectableBranch() throws Exception {
        Warehouse inactive = warehouses.save(new Warehouse("MAP-OFF-" + UUID.randomUUID(),
                "Kho ngừng phục vụ", "Địa chỉ nội bộ", WarehouseStatus.INACTIVE));
        jdbc.update("UPDATE storefront_map_locations SET warehouse_code=? WHERE code='DEMO-HAN'", inactive.getCode());
        assertThat(readLocations().get(0).path("warehouse_id").isNull()).isTrue();
        jdbc.update("UPDATE storefront_map_locations SET warehouse_code='DOES-NOT-EXIST' WHERE code='DEMO-HAN'");
        assertThat(readLocations().get(0).path("warehouse_id").isNull()).isTrue();
    }

    @Test
    void hiddenPointsAreOmittedAndAllHiddenProducesAnEmptyList() throws Exception {
        jdbc.update("UPDATE storefront_map_locations SET visible=FALSE WHERE code='DEMO-HAN'");
        assertThat(readLocations()).hasSize(2);
        jdbc.update("UPDATE storefront_map_locations SET visible=FALSE");
        assertThat(readLocations()).isEmpty();
    }

    @Test
    void readsCurrentDatabaseDetailsWithoutReturningWarehouseAddresses() throws Exception {
        Warehouse warehouse = warehouses.save(new Warehouse("PRIVATE-" + UUID.randomUUID(),
                "Kho nội bộ", "Địa chỉ bí mật của kho", WarehouseStatus.ACTIVE));
        jdbc.update("UPDATE storefront_map_locations SET name=?,address=?,latitude=?,longitude=?,demo=FALSE WHERE code='DEMO-HAN'",
                "Điểm bán đã xác minh", "Địa chỉ công khai", 21.03, 105.85);
        JsonNode point = readLocations().get(0);
        assertThat(point.path("name").asText()).isEqualTo("Điểm bán đã xác minh");
        assertThat(point.path("address").asText()).isEqualTo("Địa chỉ công khai");
        assertThat(point.path("latitude").asDouble()).isEqualTo(21.03);
        assertThat(point.path("demo").asBoolean()).isFalse();
        assertThat(readLocations().toString()).doesNotContain(warehouse.getAddress());
    }

    @Test
    void migrationDoesNotResetEditedLocationsOrBusinessData() throws Exception {
        List<Long> warehouseIds = warehouses.findAll().stream().map(Warehouse::getId).toList();
        Long orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        Long movementCount = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements", Long.class);
        jdbc.update("UPDATE storefront_map_locations SET name='Đã chỉnh sửa',visible=FALSE WHERE code='DEMO-HAN'");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM storefront_map_locations WHERE code='DEMO-HAN'", String.class))
                .isEqualTo("Đã chỉnh sửa");
        assertThat(jdbc.queryForObject("SELECT visible FROM storefront_map_locations WHERE code='DEMO-HAN'", Boolean.class)).isFalse();
        assertThat(warehouses.findAll().stream().map(Warehouse::getId).toList()).isEqualTo(warehouseIds);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isEqualTo(orderCount);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements", Long.class)).isEqualTo(movementCount);
    }

    @Test
    void newPublicReadDoesNotExposeWarehouseOperationsOrAnonymousWrites() throws Exception {
        http.perform(get("/api/v1/warehouses")).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/storefront/map-config")).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/storefront/locations")).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/storefront/map-config")).andExpect(status().isUnauthorized());
    }

    private JsonNode readLocations() throws Exception {
        byte[] body = http.perform(get("/api/v1/storefront/locations"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return json.readTree(body);
    }
}
