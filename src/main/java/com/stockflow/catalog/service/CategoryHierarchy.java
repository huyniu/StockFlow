package com.stockflow.catalog.service;

import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.common.exception.BadRequestException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Đọc cây danh mục theo một snapshot để lọc nhóm cha–con và hợp nhất gợi ý hãng. */
@Component
public class CategoryHierarchy {

    private final CategoryRepository categories;

    /** Nhận repository chung; không giữ cache có thể cũ sau khi ADMIN thêm nhóm con. */
    public CategoryHierarchy(CategoryRepository categories) {
        this.categories = categories;
    }

    /** Mỗi lượt đọc dùng một query khóa/cha, rồi tái sử dụng snapshot cho toàn bộ hãng. */
    public Tree snapshot() {
        return new Tree(categories.findHierarchy());
    }

    /** Nhánh của bộ lọc gồm chính danh mục chọn và toàn bộ hậu duệ, trước khi database phân trang. */
    public List<Long> descendantsOf(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new BadRequestException("ID danh mục phải lớn hơn 0.");
        }
        return snapshot().descendants(categoryId);
    }

    /** Snapshot không chứa entity; tập visited chặn vòng lặp nếu database bị sửa ngoài API. */
    public static final class Tree {

        private final Map<Long, Long> parents = new HashMap<>();
        private final Map<Long, List<Long>> children = new HashMap<>();

        /** Dựng hai hướng quan hệ chỉ một lần cho mỗi request. */
        private Tree(List<CategoryRepository.HierarchyLink> links) {
            for (var link : links) {
                parents.put(link.getId(), link.getParentId());
                if (link.getParentId() != null) {
                    children.computeIfAbsent(link.getParentId(), id -> new ArrayList<>()).add(link.getId());
                }
            }
        }

        /** Trả nhánh BFS không trùng ID; ID không có vẫn cho bộ lọc trả tập sản phẩm rỗng như API cũ. */
        public List<Long> descendants(Long id) {
            Set<Long> ids = new LinkedHashSet<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(id);
            while (!queue.isEmpty()) {
                Long current = queue.removeFirst();
                if (ids.add(current)) queue.addAll(children.getOrDefault(current, List.of()));
            }
            return List.copyOf(ids);
        }

        /** Hãng dùng ở nhóm con cũng xuất hiện trong bộ chọn của các nhóm cha. */
        public Set<Long> ancestors(Long id) {
            Set<Long> ids = new HashSet<>();
            for (Long current = id; current != null && ids.add(current); current = parents.get(current)) {
                // Tập visited vừa khử trùng vừa bảo vệ dữ liệu có vòng lặp do chỉnh SQL trực tiếp.
            }
            return ids;
        }
    }
}
