package backend.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;


@Configuration
@EnableScheduling
public class ApiConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.registerModule(produceJavaTimeModuleInstance());
        return objectMapper;
    }

    @Bean
    public ObjectWriter objectWriter(ObjectMapper objectMapper) {
        return objectMapper.writerWithDefaultPrettyPrinter();
    }

    @Bean
    public WebClient webClient() throws SSLException {
        SslContext sslContext = SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        ClientHttpConnector httpConnector = new ReactorClientHttpConnector(opt -> {
            opt.sslContext(sslContext);
            opt.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                    .compression(true)
                    .afterNettyContextInit(ctx -> {
                        ctx.addHandlerLast(new ReadTimeoutHandler(5000, TimeUnit.MILLISECONDS));
                    });
        });

        return WebClient.builder().clientConnector(httpConnector).build();
    }

    @Bean
    @Scope("prototype")
    public Logger produceLogger(InjectionPoint injectionPoint) {
        Class<?> declaringClass = injectionPoint.getMember().getDeclaringClass();
        return LoggerFactory.getLogger(declaringClass);
    }

    /**
     * Method returns {@link JavaTimeModule} configured to (de)serialize {@link LocalDateTime} to
     * UTC format, such as '2011-12-03T10:15:30Z' which not supported by default.
     *
     * @return instance of configured {@link JavaTimeModule}.
     */
    private JavaTimeModule produceJavaTimeModuleInstance() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        DateTimeFormatter defaultFormatter = DateTimeFormatter.ISO_INSTANT;
        ZoneOffset defaultZoneOffset = ZoneOffset.UTC;

        javaTimeModule.addSerializer(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                Instant instant = value.toInstant(defaultZoneOffset);
                String stringDate = defaultFormatter.format(instant);
                gen.writeString(stringDate);
            }
        });
        javaTimeModule.addDeserializer(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String stringDate = p.getText().trim();
                Instant instant = Instant.from(defaultFormatter.parse(stringDate));
                return LocalDateTime.ofInstant(instant, ZoneId.of(defaultZoneOffset.getId()));
            }
        });

        return javaTimeModule;
    }
}
