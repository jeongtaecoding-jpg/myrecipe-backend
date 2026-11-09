package com.myrecipe.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mysql.MySQLContainer;

// 통합 테스트의 공통 부모 (CODE_CONVENTION 테스트 절).
// 테스트할 때만 진짜 MySQL 8.4 컨테이너를 띄우고, 개발용 DB(.env·docker compose)는 쓰지 않는다.
// 컨테이너는 static으로 한 번만 띄워서 모든 통합 테스트 클래스가 함께 쓴다(클래스마다 새로 띄우면 느리다).
// Redis 컨테이너는 Redis를 쓰는 코드(Refresh Token)가 들어올 때 여기에 추가한다.
@SpringBootTest
public abstract class IntegrationTestSupport {

    @ServiceConnection   // 컨테이너의 주소·계정을 스프링 DB 설정으로 자동 연결
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start();
    }
}
