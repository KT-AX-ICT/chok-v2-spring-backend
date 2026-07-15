package com.choks.chokchok.config;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Hibernate 7의 JSON FormatMapper 자동 감지는 Jackson 2(com.fasterxml)를 찾는데,
 * Spring Boot 4는 Jackson 3(tools.jackson)만 올린다. 그 간극을 Jackson 3 어댑터로 메꿔
 * @JdbcTypeCode(JSON) 컬럼(report.trigger_info·result) 저장·조회를 가능케 한다.
 */
@Configuration
public class JsonMappingConfig {

    @Bean
    public HibernatePropertiesCustomizer jsonFormatMapperCustomizer(ObjectMapper objectMapper) {
        return props -> props.put(
                AvailableSettings.JSON_FORMAT_MAPPER, new Jackson3FormatMapper(objectMapper));
    }

    /** Jackson 3 ObjectMapper로 Hibernate FormatMapper 계약(fromString/toString)을 구현. */
    static final class Jackson3FormatMapper implements FormatMapper {

        private final ObjectMapper mapper;

        Jackson3FormatMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T fromString(CharSequence charSequence, JavaType<T> javaType, WrapperOptions options) {
            return (T) mapper.readValue(charSequence.toString(), mapper.constructType(javaType.getJavaType()));
        }

        @Override
        public <T> String toString(T value, JavaType<T> javaType, WrapperOptions options) {
            return mapper.writeValueAsString(value);
        }
    }
}
