package re.kr.icuh.drought.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import re.kr.icuh.drought.persistence.article.entity.DocumentType;
import re.kr.icuh.drought.persistence.article.repository.DocumentTypeRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan(basePackages = "re.kr.icuh.drought.persistence")
@EnableJpaRepositories(basePackages = "re.kr.icuh.drought.persistence")
class PersistenceSliceTest {

    @Autowired
    private DocumentTypeRepository documentTypeRepository;

    @Test
    void articleReferenceRepositoriesAreWired() {
        DocumentType saved = documentTypeRepository.save(new DocumentType(
                null,
                "보고서",
                "Report",
                "REPORT",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "ACTIVE"
        ));

        assertThat(documentTypeRepository.findByCode("REPORT"))
                .isPresent()
                .get()
                .extracting(DocumentType::getId)
                .isEqualTo(saved.getId());
    }

    @SpringBootConfiguration
    static class TestApplication {
    }
}
