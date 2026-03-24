package com.tenpo.challenge.config;

import com.tenpo.challenge.domain.enums.CallStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;

import java.util.List;

@Configuration
@EnableR2dbcAuditing
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, List.of(
            new CallStatusWritingConverter(),
            new CallStatusReadingConverter()
        ));
    }

    @WritingConverter
    static class CallStatusWritingConverter implements Converter<CallStatus, String> {
        @Override
        public String convert(CallStatus source) {
            return source.name();
        }
    }

    @ReadingConverter
    static class CallStatusReadingConverter implements Converter<String, CallStatus> {
        @Override
        public CallStatus convert(String source) {
            return CallStatus.valueOf(source);
        }
    }
}
