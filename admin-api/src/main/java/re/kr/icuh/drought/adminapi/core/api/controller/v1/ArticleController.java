package re.kr.icuh.drought.adminapi.core.api.controller.v1;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/article")
public class ArticleController {

	@GetMapping("/create-list")
	public String createList() {
		return "article/article-create-list";
	}

	@GetMapping("/update-list")
	public String updateList() {
		return "article/article-update-list";
	}

	@GetMapping("/delete-list")
	public String deleteList() {
		return "article/article-delete-list";
	}

    // 생성 요청 상세보기
    // TODO: create-detail로 url 변경
	@GetMapping("/detail/{id}")
	public String articleDetail(@PathVariable("id") Long id) {
		return "article/article-create-detail";
	}

    // 삭제 요청 상세보기
    @GetMapping("/delete-detail/{id}")
    public String articleDeleteDetail(@PathVariable("id") Long id) {
        return "article/article-delete-detail";
    }

    // 수정 요청 상세보기
    @GetMapping("/update-detail/{id}")
    public String articleUpdateDetail(@PathVariable("id") Long id) {
        return "article/article-update-detail";
    }
}
