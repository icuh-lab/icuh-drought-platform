package re.kr.icuh.drought.persistence.openapi.drought.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Converter
public class KeywordsJsonConverter implements AttributeConverter<List<String>, String> {

    private final ObjectMapper objectMapper;

    public KeywordsJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
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
    public List<String> convertToEntityAttribute(String dbData) {
        if (StringUtils.hasText(dbData)) {
            try {
                return objectMapper.readValue(dbData, new TypeReference<List<String>>() {
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
