package re.kr.icuh.drought.persistence.article.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import re.kr.icuh.drought.domain.article.UpdateArticleRequest;

import java.util.List;

public class NewFileRequestJsonConverter implements AttributeConverter<List<UpdateArticleRequest.NewFileRequest>, String> {

    private final ObjectMapper objectMapper;

    public NewFileRequestJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convertToDatabaseColumn(List<UpdateArticleRequest.NewFileRequest> attribute) {
        if (ObjectUtils.isEmpty(attribute)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<UpdateArticleRequest.NewFileRequest> convertToEntityAttribute(String dbData) {
        if (StringUtils.hasText(dbData)) {
            try {
                return objectMapper.readValue(dbData, new TypeReference<List<UpdateArticleRequest.NewFileRequest>>() {
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }
}
