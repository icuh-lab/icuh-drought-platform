package re.kr.icuh.drought.publicapi.article.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import re.kr.icuh.drought.publicapi.article.dto.request.UpdateArticleRequest;

@Converter
public class UpdateArticleRequestJsonConverter implements AttributeConverter<UpdateArticleRequest, String> {

    private final ObjectMapper objectMapper;

    public UpdateArticleRequestJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convertToDatabaseColumn(UpdateArticleRequest entityAttribute) {
        if (ObjectUtils.isEmpty(entityAttribute)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(entityAttribute);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public UpdateArticleRequest convertToEntityAttribute(String dbData) {
        if (StringUtils.hasText(dbData)) {
            try {
                return objectMapper.readValue(dbData, UpdateArticleRequest.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
