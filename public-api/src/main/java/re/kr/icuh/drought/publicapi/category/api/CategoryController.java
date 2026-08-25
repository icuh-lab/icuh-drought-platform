package re.kr.icuh.drought.publicapi.category.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import re.kr.icuh.drought.publicapi.category.application.CategoryService;
import re.kr.icuh.drought.publicapi.category.dto.response.CategoryResponse;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/article-categories")
    public CategoryResponse getCategory() {
        return categoryService.getCategories();
    }
}
