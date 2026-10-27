package com.stockflow.warehouse;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Địa chỉ kho chỉ mở cho role vận hành; chỉ ADMIN sửa được địa chỉ kho và cửa hàng công khai. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WarehouseDirectoryIntegrationTest {

    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired WarehouseRepository warehouses;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtTokenProvider jwt;
    @Autowired JdbcTemplate jdbc;

    private final String locationCode = "DIR-" + UUID.randomUUID();

    /** Bảng điểm bản đồ dùng chung; StorefrontMapIntegrationTest đếm đúng ba điểm demo. */
    @AfterEach
    void removeLocation() {
        jdbc.update("DELETE FROM storefront_map_locations WHERE code = ?", locationCode);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "WAREHOUSE_STAFF"})
    void operatorsSeeWarehouseAndStoreAddresses(String role) throws Exception {
        Warehouse linked = linkedWarehouse();
        JsonNode row = directoryRow(token(role), linked.getId());
        assertThat(row.path("address").asText()).isEqualTo("Địa chỉ kho nội bộ");
        assertThat(row.path("store_name").asText()).isEqualTo("Cửa hàng kiểm thử");
        assertThat(row.path("store_address").asText()).isEqualTo("Địa chỉ cửa hàng kiểm thử");
    }

    @Test
    void customersAndGuestsCannotReadWarehouseAddresses() throws Exception {
        http.perform(get("/api/v1/warehouses/directory").header("Authorization", "Bearer " + token("CUSTOMER")))
                .andExpect(status().isForbidden());
        http.perform(get("/api/v1/warehouses/directory")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminUpdatesWarehouseAddressAndStoreShownToCustomers() throws Exception {
        Warehouse linked = linkedWarehouse();
        String admin = token("ADMIN");
        http.perform(patch("/api/v1/warehouses/" + linked.getId() + "/address")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"  Kho mới, Long Biên  \"}"))
                .andExpect(status().isOk());
        http.perform(patch("/api/v1/warehouses/" + linked.getId() + "/store-location")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"StockFlow Mới\",\"address\":\"Số 1 Phố Mới\"}"))
                .andExpect(status().isNoContent());

        JsonNode row = directoryRow(admin, linked.getId());
        assertThat(row.path("address").asText()).isEqualTo("Kho mới, Long Biên");
        assertThat(row.path("store_name").asText()).isEqualTo("StockFlow Mới");
        assertThat(row.path("store_address").asText()).isEqualTo("Số 1 Phố Mới");

        JsonNode branch = StreamSupport.stream(json.readTree(http.perform(get("/api/v1/storefront/branches"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).spliterator(), false)
                .filter(node -> node.path("id").asLong() == linked.getId())
                .findFirst().orElseThrow();
        assertThat(branch.path("name").asText()).isEqualTo("StockFlow Mới");
        assertThat(branch.has("address")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "WAREHOUSE_STAFF", "CUSTOMER"})
    void onlyAdminCanEditAddresses(String role) throws Exception {
        Warehouse linked = linkedWarehouse();
        String token = token(role);
        http.perform(patch("/api/v1/warehouses/" + linked.getId() + "/address")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"Không được đổi\"}"))
                .andExpect(status().isForbidden());
        http.perform(patch("/api/v1/warehouses/" + linked.getId() + "/store-location")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Không được đổi\",\"address\":\"Không được đổi\"}"))
                .andExpect(status().isForbidden());
        assertThat(warehouses.findById(linked.getId()).orElseThrow().getAddress()).isEqualTo("Địa chỉ kho nội bộ");
    }

    @Test
    void blankValuesAndMissingStoreAreRejected() throws Exception {
        Warehouse unlinked = warehouse();
        String admin = token("ADMIN");
        http.perform(patch("/api/v1/warehouses/" + unlinked.getId() + "/address")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"   \"}"))
                .andExpect(status().isBadRequest());
        http.perform(patch("/api/v1/warehouses/" + unlinked.getId() + "/store-location")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cửa hàng\",\"address\":\"Địa chỉ\"}"))
                .andExpect(status().isNotFound());
        http.perform(patch("/api/v1/warehouses/" + Long.MAX_VALUE + "/address")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"Địa chỉ\"}"))
                .andExpect(status().isNotFound());
    }

    private Warehouse warehouse() {
        return warehouses.save(new Warehouse("DIR-" + UUID.randomUUID(), "Kho kiểm thử",
                "Địa chỉ kho nội bộ", WarehouseStatus.ACTIVE));
    }

    private Warehouse linkedWarehouse() {
        Warehouse warehouse = warehouse();
        jdbc.update("INSERT INTO storefront_map_locations (code, name, address, latitude, longitude, warehouse_code) "
                + "VALUES (?, 'Cửa hàng kiểm thử', 'Địa chỉ cửa hàng kiểm thử', 21.0, 105.8, ?)",
                locationCode, warehouse.getCode());
        return warehouse;
    }

    private String token(String role) {
        return jwt.generateToken(users.save(verifiedUser(UUID.randomUUID() + "@example.com", "hash-kiểm-thử",
                "Người kiểm thử kho", roles.findByName(role).orElseThrow())));
    }

    private JsonNode directoryRow(String token, Long warehouseId) throws Exception {
        JsonNode rows = json.readTree(http.perform(get("/api/v1/warehouses/directory")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return StreamSupport.stream(rows.spliterator(), false)
                .filter(row -> row.path("id").asLong() == warehouseId)
                .findFirst().orElseThrow();
    }
}
