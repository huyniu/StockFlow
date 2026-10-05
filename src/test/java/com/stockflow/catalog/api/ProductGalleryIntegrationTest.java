package com.stockflow.catalog.api;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm chứng bộ ảnh qua JWT thật, validation, migration, persistence và catalog công khai.
 * Mỗi ca rollback dữ liệu riêng; không tải ảnh từ CDN và không phụ thuộc sản phẩm đang dùng để demo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductGalleryIntegrationTest {

    private static final String COVER = "https://example.com/cover.jpg";
    private static final List<String> PHOTOS = List.of(
            "https://example.com/front.jpg?size=800&quality=90",
            "/assets/products/back.jpg",
            "http://example.com/side.jpg");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider jwt;
    @Autowired EntityManager entities;

    private Category category;
    private String adminToken;

    /** Actor và danh mục riêng đảm bảo test không dựa vào catalog nhập tay hoặc dữ liệu seed. */
    @BeforeEach
    void setUp() {
        adminToken = token("ADMIN");
        category = categories.save(new Category(
                "Danh mục bộ ảnh " + UUID.randomUUID(),
                "bo-anh-" + UUID.randomUUID()));
    }

    /** Lưu thật và đọc công khai đúng thứ tự; ảnh bìa và nội dung tiếng Việt tiếp tục giữ nguyên. */
    @Test
    void createAndPublicReadPreserveGalleryOrder() throws Exception {
        List<String> spaced = PHOTOS.stream().map(url -> "  " + url + "  ").toList();
        JsonNode created = create(spaced);
        long id = created.path("id").asLong();
        assertThat(gallery(created)).containsExactlyElementsOf(PHOTOS);
        products.flush();
        entities.clear();
        assertThat(products.findById(id).orElseThrow().getImageUrls()).containsExactlyElementsOf(PHOTOS);
        assertThat(products.findById(id).orElseThrow().getImageUrl()).isEqualTo(COVER);

        mvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image_url").value(COVER))
                .andExpect(jsonPath("$.image_urls[0]").value(PHOTOS.get(0)))
                .andExpect(jsonPath("$.image_urls[2]").value(PHOTOS.get(2)));
        mvc.perform(get("/api/v1/products").param("q", created.path("sku").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].image_urls[1]").value(PHOTOS.get(1)));
    }

    /** Request cũ không có image_urls vẫn tạo được và trả danh sách rỗng thay vì null. */
    @Test
    void legacyCreateHasEmptyGallery() throws Exception {
        JsonNode created = create(null);
        assertThat(created.path("image_urls").isArray()).isTrue();
        assertThat(gallery(created)).isEmpty();
        assertThat(created.path("image_url").asText()).isEqualTo(COVER);
    }

    /** Đổi thứ tự hoặc giảm số ảnh phải được lưu lại mà không nhầm vị trí hay thay giá/SKU. */
    @Test
    void patchReordersAndReplacesGallery() throws Exception {
        JsonNode created = create(PHOTOS);
        long id = created.path("id").asLong();
        List<String> reversed = List.of(PHOTOS.get(2), PHOTOS.get(0), PHOTOS.get(1));
        update(id, Map.of("image_urls", reversed))
                .andExpect(jsonPath("$.image_urls[0]").value(PHOTOS.get(2)))
                .andExpect(jsonPath("$.unit_price").value(250000));
        products.flush();
        entities.clear();
        assertThat(products.findById(id).orElseThrow().getImageUrls()).containsExactlyElementsOf(reversed);
        update(id, Map.of("image_urls", List.of(PHOTOS.get(1))));
        products.flush();
        entities.clear();
        assertThat(products.findById(id).orElseThrow().getImageUrls()).containsExactly(PHOTOS.get(1));
        assertThat(products.findById(id).orElseThrow().getSku()).isEqualTo(created.path("sku").asText());
    }

    /** Sửa giá hoặc gửi null không được xóa ảnh bổ sung đã lưu. */
    @Test
    void omittedOrNullGalleryPreservesStoredPhotos() throws Exception {
        long id = create(PHOTOS).path("id").asLong();
        Map<String, Object> payload = new HashMap<>();
        payload.put("unit_price", new BigDecimal("260000"));
        update(id, payload).andExpect(jsonPath("$.image_urls.length()").value(3));
        payload.put("image_urls", null);
        update(id, payload).andExpect(jsonPath("$.image_urls[0]").value(PHOTOS.get(0)));
        products.flush();
        entities.clear();
        assertThat(products.findById(id).orElseThrow().getImageUrls()).containsExactlyElementsOf(PHOTOS);
    }

    /** Mảng rỗng xóa ảnh bổ sung; ảnh bìa, mô tả và các trường nghiệp vụ không bị xóa theo. */
    @Test
    void emptyGalleryClearsOnlyAdditionalPhotos() throws Exception {
        long id = create(PHOTOS).path("id").asLong();
        update(id, Map.of("image_urls", List.of()))
                .andExpect(jsonPath("$.image_urls").isEmpty())
                .andExpect(jsonPath("$.image_url").value(COVER))
                .andExpect(jsonPath("$.description").value("Mô tả bộ ảnh kiểm thử"));
        products.flush();
        entities.clear();
        assertThat(products.findById(id).orElseThrow().getImageUrls()).isEmpty();
    }

    /** Biên tám ảnh phải chấp nhận được cả ở HTTP và bảng product_images. */
    @Test
    void eightPhotosAreAccepted() throws Exception {
        List<String> photos = photoList(8);
        long id = create(photos).path("id").asLong();
        products.flush();
        entities.clear();
        assertThat(products.findById(id).orElseThrow().getImageUrls()).containsExactlyElementsOf(photos);
    }

    /** Quá giới hạn bị chặn 400 trước khi lưu; sản phẩm và bộ ảnh đang có được bảo toàn. */
    @Test
    void ninePhotosAreRejectedOnCreateAndPatch() throws Exception {
        assertRejectedGallery(photoList(9));
    }

    /** URL trùng sau chuẩn hóa phải bị chặn; request sửa giá kèm bộ ảnh sai không được lưu một phần. */
    @Test
    void duplicatePhotosAreRejectedWithOtherUpdates() throws Exception {
        long id = create(PHOTOS).path("id").asLong();
        products.flush();
        mvc.perform(patch("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "unit_price", 990000,
                                "image_urls", List.of(PHOTOS.get(0), " " + PHOTOS.get(0) + " ")))))
                .andExpect(status().isBadRequest());
        entities.clear();
        assertThat(products.findById(id).orElseThrow().getUnitPrice()).isEqualByComparingTo("250000");
        assertThat(products.findById(id).orElseThrow().getImageUrls()).containsExactlyElementsOf(PHOTOS);
    }

    /** Mỗi URL trong mảng phải chịu cùng validation an toàn như ảnh bìa. */
    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:image/svg+xml;base64,AAAA",
            "file:///C:/private.jpg",
            "//example.com/photo.jpg",
            "https://",
            "https://example.com/photo with spaces.jpg",
            "https://user:password@example.com/photo.jpg",
            "/assets/../application.yml",
            "/assets/%2e%2e/application.yml"
    })
    void invalidPhotoIsRejectedInGallery(String value) throws Exception {
        assertRejectedGallery(List.of(value));
    }

    /** Null hoặc chuỗi trống bên trong mảng không được tạo hàng ảnh rỗng trong database. */
    @Test
    void nullAndBlankGalleryEntriesAreRejected() throws Exception {
        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        assertRejectedGallery(withNull);
        assertRejectedGallery(List.of("   "));
    }

    /** Giới hạn URL 2048 ký tự áp dụng cho từng ảnh, tránh lỗi database thay vì lỗi validation. */
    @Test
    void oversizedGalleryUrlIsRejected() throws Exception {
        assertRejectedGallery(List.of("https://example.com/" + "a".repeat(2048)));
    }

    /** Gallery phải là mảng, không nhận một chuỗi hay object thay cho danh sách. */
    @ParameterizedTest
    @ValueSource(strings = {"\"https://example.com/photo.jpg\"", "{\"url\":\"https://example.com/photo.jpg\"}"})
    void wrongGalleryJsonTypeIsRejected(String value) throws Exception {
        long id = create(PHOTOS).path("id").asLong();
        mvc.perform(patch("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image_urls\":" + value + "}"))
                .andExpect(status().isBadRequest());
        assertThat(products.findById(id).orElseThrow().getImageUrls()).containsExactlyElementsOf(PHOTOS);
    }

    /** CUSTOMER/MANAGER/STAFF chỉ đọc catalog; POST/PATCH bộ ảnh vẫn dành riêng cho ADMIN. */
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MANAGER", "WAREHOUSE_STAFF"})
    void nonAdminCannotChangeGallery(String role) throws Exception {
        long id = create(PHOTOS).path("id").asLong();
        String forbiddenToken = token(role);
        Map<String, Object> createPayload = payload();
        createPayload.put("image_urls", PHOTOS);
        mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + forbiddenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(createPayload)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + forbiddenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("image_urls", List.of()))))
                .andExpect(status().isForbidden());
        assertThat(products.findById(id).orElseThrow().getImageUrls()).containsExactlyElementsOf(PHOTOS);
    }

    /** Trang chi tiết công khai không mở quyền PATCH bộ ảnh cho khách chưa đăng nhập. */
    @Test
    void anonymousCannotPatchGallery() throws Exception {
        long id = create(PHOTOS).path("id").asLong();
        mvc.perform(patch("/api/v1/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image_urls\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    /** Tải collection ảnh không được làm sai số trang, tổng số sản phẩm hoặc trộn ảnh giữa các sản phẩm. */
    @Test
    void pagedCatalogKeepsEachProductsOwnGallery() throws Exception {
        List<JsonNode> created = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            created.add(create(List.of("https://example.com/product-" + index + ".jpg")));
        }
        products.flush();
        entities.clear();
        mvc.perform(get("/api/v1/products")
                        .param("categoryId", category.getId().toString())
                        .param("size", "2")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(3))
                .andExpect(jsonPath("$.total_pages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].image_urls[0]").value("https://example.com/product-0.jpg"));
        mvc.perform(get("/api/v1/products")
                        .param("categoryId", category.getId().toString())
                        .param("size", "2")
                        .param("page", "1")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(created.get(2).path("id").asLong()))
                .andExpect(jsonPath("$.content[0].image_urls[0]").value("https://example.com/product-2.jpg"));
    }

    /** Đối chiếu cả POST/PATCH: dữ liệu không hợp lệ không tạo sản phẩm hoặc sửa bộ ảnh đang có. */
    private void assertRejectedGallery(List<String> photos) throws Exception {
        long before = products.count();
        Map<String, Object> request = payload();
        request.put("image_urls", photos);
        mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertThat(products.count()).isEqualTo(before);
        long id = create(PHOTOS).path("id").asLong();
        mvc.perform(patch("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("image_urls", photos))))
                .andExpect(status().isBadRequest());
        assertThat(products.findById(id).orElseThrow().getImageUrls()).containsExactlyElementsOf(PHOTOS);
    }

    /** Payload ADMIN tối thiểu với ảnh bìa và mô tả, không phụ thuộc ảnh trong seed. */
    private Map<String, Object> payload() {
        Map<String, Object> request = new HashMap<>();
        request.put("sku", "GALLERY-" + UUID.randomUUID());
        request.put("name", "Sản phẩm bộ ảnh kiểm thử");
        request.put("category_id", category.getId());
        request.put("unit_price", new BigDecimal("250000"));
        request.put("status", "ACTIVE");
        request.put("image_url", COVER);
        request.put("description", "Mô tả bộ ảnh kiểm thử");
        return request;
    }

    /** Tạo qua controller thật rồi parse UTF-8 để các assertion tiếng Việt không lệch mã hóa. */
    private JsonNode create(List<String> photos) throws Exception {
        Map<String, Object> request = payload();
        if (photos != null) request.put("image_urls", photos);
        return json.readTree(mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** PATCH bằng JWT ADMIN để kiểm tra thứ tự, tương thích và thao tác xóa ảnh bổ sung. */
    private ResultActions update(long id, Map<String, Object> request) throws Exception {
        return mvc.perform(patch("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    /** Chuyển mảng JSON thành danh sách để so sánh chính xác thứ tự thay vì chỉ đếm ảnh. */
    private List<String> gallery(JsonNode product) {
        List<String> urls = new ArrayList<>();
        product.path("image_urls").forEach(value -> urls.add(value.asText()));
        return urls;
    }

    /** Sinh các URL khác nhau khi kiểm tra biên số lượng, không gọi tới máy chủ ảnh. */
    private List<String> photoList(int size) {
        return IntStream.range(0, size).mapToObj(index -> "https://example.com/photo-" + index + ".jpg").toList();
    }

    /** JWT lấy role của user đã lưu, đảm bảo filter và method security cùng được kiểm tra. */
    private String token(String role) {
        User actor = users.save(verifiedUser(
                "gallery-" + UUID.randomUUID() + "@example.com",
                "hash-kiểm-thử",
                "Người kiểm thử bộ ảnh",
                roles.findByName(role).orElseThrow()));
        return jwt.generateToken(actor);
    }
}
