package com.stockflow.catalog.api;

import com.stockflow.catalog.domain.*;
import com.stockflow.catalog.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductAttributeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVersionRepository versions;
    @Autowired ProductVariantRepository variants;
    Category category;
    Product model, sku8, sku16, plain;
    ProductVariant color8, color16;

    @BeforeEach void setup() {
        String key=UUID.randomUUID().toString();
        category=categories.save(new Category(key,key));
        model=product("Model", List.of(new ProductSpecification("RAM","16 GB"), new ProductSpecification("CPU","Intel Core i5")));
        sku8=product("SKU8",List.of()); sku16=product("SKU16",List.of());
        plain=product("Plain",List.of(new ProductSpecification("RAM","128 GB"),new ProductSpecification("CPU","AMD Ryzen 5")));
        var small=new ProductVersion(model,"Small"); small.replaceSpecifications(List.of(new ProductSpecification("RAM","8 GB")));
        small=versions.save(small);
        var large=versions.save(new ProductVersion(model,"Large"));
        color8=variants.save(new ProductVariant(model,sku8,small,"Black",null));
        color16=variants.save(new ProductVariant(model,sku16,large,"White",null));
    }
    private Product product(String name,List<ProductSpecification> specs) {
        var p=new Product(category,UUID.randomUUID().toString(),name,new BigDecimal("100"),ProductStatus.ACTIVE);
        p.replaceSpecifications(specs); return products.save(p);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder filter(String name,String value,boolean grouped) {
        return get("/api/v1/products").param("categoryId",category.getId().toString())
                .param("status","ACTIVE").param("grouped",String.valueOf(grouped))
                .param("specificationName",name).param("specificationValue",value);
    }
    @Test void groupedVersionOverrideMatchesModel() throws Exception {
        mvc.perform(filter("RAM","8GB",true)).andExpect(status().isOk())
                .andExpect(jsonPath("total_elements").value(1)).andExpect(jsonPath("content[0].id").value(model.getId().intValue()));
    }
    @Test void ungroupedFilterMatchesEffectiveSkuSpecification() throws Exception {
        mvc.perform(filter("ram"," 8 gb ",false).param("q","SKU8")).andExpect(status().isOk())
                .andExpect(jsonPath("total_elements").value(1)).andExpect(jsonPath("content[0].id").value(sku8.getId().intValue()));
    }
    @Test void inheritedBaseSpecificationWorks() throws Exception {
        mvc.perform(filter("RAM","16GB",false)).andExpect(status().isOk())
                .andExpect(jsonPath("total_elements").value(2))
                .andExpect(jsonPath("content[1].id").value(sku16.getId().intValue()));
    }
    @Test void versionOverrideDoesNotMatchOldBaseValue() throws Exception {
        color16.archive(); variants.saveAndFlush(color16);
        mvc.perform(filter("RAM","16GB",true)).andExpect(status().isOk()).andExpect(jsonPath("total_elements").value(0));
    }
    @Test void archivedVersionCannotMatch() throws Exception {
        color8.archive(); variants.saveAndFlush(color8);
        mvc.perform(filter("RAM","8GB",true)).andExpect(status().isOk()).andExpect(jsonPath("total_elements").value(0));
    }
    @Test void plainProductAndExactValueMatchWithoutSubstringMistakes() throws Exception {
        mvc.perform(filter("RAM","128GB",true)).andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(plain.getId().intValue()));
        mvc.perform(filter("RAM","28GB",true)).andExpect(status().isOk()).andExpect(jsonPath("total_elements").value(0));
    }
    @Test void filtersComposeBeforePagination() throws Exception {
        mvc.perform(filter("CPU","Intel Core i5",true).param("size","1").param("q","Model").param("maxPrice","100"))
                .andExpect(status().isOk()).andExpect(jsonPath("total_elements").value(1));
        mvc.perform(filter("CPU","Intel Core i5",true).param("maxPrice","99"))
                .andExpect(status().isOk()).andExpect(jsonPath("total_elements").value(0));
    }
    @Test void partialAndOversizedFiltersAreRejected() throws Exception {
        mvc.perform(get("/api/v1/products").param("specificationName","RAM")).andExpect(status().isBadRequest());
        mvc.perform(filter("RAM","x".repeat(1001),true)).andExpect(status().isBadRequest());
    }
    @Test void suggestionsComeFromStoredSpecificationData() throws Exception {
        products.flush();
        mvc.perform(get("/api/v1/products/specification-options")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'RAM' && @.value == '8 GB')]").isNotEmpty());
    }
}
