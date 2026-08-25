package re.kr.icuh.drought.persistence.article.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import re.kr.icuh.drought.persistence.article.entity.SubjectDomain;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectDomainRepository extends JpaRepository<SubjectDomain, Long> {
    Optional<SubjectDomain> findByCode(String code);

    List<SubjectDomain> findByStatus(String status);
}
