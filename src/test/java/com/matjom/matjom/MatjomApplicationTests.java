package com.matjom.matjom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.junit.jupiter.api.Disabled;

@SpringBootTest
@Disabled("Requires Docker to run Testcontainers")
@ActiveProfiles("test")
@Testcontainers
class MatjomApplicationTests {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName.parse("postgis/postgis:16-3.4") // 테스트 컨테이너 이미지.
            .asCompatibleSubstituteFor("postgres");                                                 // PostgreSQL 호환 이미지로 사용한다.

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGIS_IMAGE)          // 테스트용 PostgreSQL 컨테이너.
            .withDatabaseName("matjom")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("tc-init.sql");

    @DynamicPropertySource
    static void datasourceConfig(DynamicPropertyRegistry registry) {
        // Given
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);                                // 컨테이너 JDBC URL을 설정한다.
        registry.add("spring.datasource.username", POSTGRES::getUsername);                          // 데이터베이스 사용자명을 설정한다.
        registry.add("spring.datasource.password", POSTGRES::getPassword);                          // 데이터베이스 비밀번호를 설정한다.
        registry.add("jwt.secret-base64", () -> "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"); // JWT 서명 키를 설정한다.
        registry.add("JWT_SECRET", () -> "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");      // 레거시 환경 변수를 지원한다.
    }

    // 애플리케이션 컨텍스트가 정상 기동되는지 확인한다.
    @Test
    void contextLoads() {
        // Given & When
        // 스프링 부트가 컨텍스트를 로드한다.

        // Then
        // 예외가 발생하지 않으면 성공으로 간주한다.
    }
}
